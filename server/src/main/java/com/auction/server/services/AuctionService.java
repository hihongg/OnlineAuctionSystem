package com.auction.server.services;

import com.auction.server.dao.BidDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.network.AuctionServer;
import com.auction.shared.models.Item;
import com.auction.shared.models.Message;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AuctionService {

    private final ItemDAO  itemDAO;
    private final BidDAO   bidDAO;
    private final AuctionServer server;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Gson gson = new Gson();

    // =========================================================================
    // AUTO-BID STORAGE
    // Key  = itemId
    // Value = danh sách AutoBidEntry, sắp xếp theo registeredAt tăng dần
    // =========================================================================
    private final Map<Integer, List<AutoBidEntry>> autoBids = new ConcurrentHashMap<>();

    // =========================================================================
    // PER-ITEM LOCK MAP — FIX CHO 2 VẤN ĐỀ ĐỒNG THỜI
    //
    // VẤN ĐỀ CŨ 1 (triggerAutoBids):
    //   triggerAutoBids() dùng synchronized(this) → toàn bộ class bị khóa
    //   trong khi gọi bidDAO.placeBidTransaction() (I/O tới MySQL, ~50-200ms).
    //   Hậu quả: registerAutoBid() và cancelAutoBid() của MỌI item đều bị block,
    //   dù chúng không liên quan đến item đang xử lý.
    //
    // VẤN ĐỀ CŨ 2 (applyAntiSniping):
    //   applyAntiSniping() không synchronized → race condition:
    //   2 bid đến cùng lúc đều thấy remaining < 60s, cả 2 gọi updateEndTime()
    //   → gia hạn 2 lần (10 phút) thay vì 1 lần (5 phút).
    //
    // GIẢI PHÁP: Per-item lock thay vì lock toàn class.
    //   - Mỗi itemId có 1 Object lock riêng.
    //   - Item A và Item B có thể xử lý auto-bid SONG SONG.
    //   - Chỉ 2 thread cùng thao tác trên CÙNG 1 item mới phải chờ nhau.
    //   - Lock chỉ giữ trong thời gian đọc/sửa danh sách (microseconds),
    //     KHÔNG giữ trong khi gọi DB.
    // =========================================================================
    private final ConcurrentHashMap<Integer, Object> itemLocks = new ConcurrentHashMap<>();

    // =========================================================================
    // DEPOSIT REQUEST STORAGE
    // Lưu yêu cầu nạp tiền của Bidder/Seller chờ Admin duyệt.
    // Dùng in-memory (không cần thêm bảng DB).
    // =========================================================================
    private final ConcurrentHashMap<Integer, DepositRequest> depositRequests = new ConcurrentHashMap<>();
    private final AtomicInteger depositIdCounter = new AtomicInteger(1);

    /** Thêm yêu cầu nạp tiền mới, trả về ID của yêu cầu. */
    public int addDepositRequest(String username, double amount) {
        int id = depositIdCounter.getAndIncrement();
        depositRequests.put(id, new DepositRequest(id, username, amount));
        System.out.printf("[DEPOSIT] Yêu cầu #%d: %s muốn nạp $%.2f — chờ admin duyệt%n",
                id, username, amount);
        return id;
    }

    /** Lấy tất cả yêu cầu (admin dùng để hiển thị). */
    public List<DepositRequest> getAllDepositRequests() {
        return depositRequests.values().stream()
                .sorted(Comparator.comparingLong(r -> r.createdAt))
                .collect(Collectors.toList());
    }

    /** Lấy yêu cầu theo ID. */
    public DepositRequest getDepositRequest(int id) {
        return depositRequests.get(id);
    }

    /** Admin duyệt yêu cầu. */
    public void approveDepositRequest(int id) {
        DepositRequest req = depositRequests.get(id);
        if (req != null) req.status = "APPROVED";
    }

    /** Admin từ chối yêu cầu. */
    public void rejectDepositRequest(int id) {
        DepositRequest req = depositRequests.get(id);
        if (req != null) req.status = "REJECTED";
    }

    /**
     * Trả về lock object cho một itemId cụ thể.
     * computeIfAbsent đảm bảo mỗi itemId chỉ có đúng 1 lock object,
     * dù nhiều thread gọi cùng lúc.
     */
    private Object getLock(int itemId) {
        return itemLocks.computeIfAbsent(itemId, k -> new Object());
    }

    public AuctionService(AuctionServer server) {
        this.itemDAO = new ItemDAO();
        this.bidDAO  = new BidDAO();
        this.server  = server;
        startScheduler();
        System.out.println("[SERVICE] AuctionService đã sẵn sàng.");
    }

    // =========================================================================
    // SCHEDULER — chạy mỗi 1 giây
    // =========================================================================
    public void startScheduler() {
        scheduler.scheduleAtFixedRate(this::refreshAuctionsStatus, 0, 1, TimeUnit.SECONDS);
        System.out.println("[SERVICE] Scheduler đã chạy (kiểm tra mỗi 1 giây).");
    }

    public void refreshAuctionsStatus() {
        // Bước 1: OPEN → RUNNING
        itemDAO.activatePendingItems();

        // Bước 2: RUNNING → FINISHED
        // PERFORMANCE FIX: Dùng getRunningItemsToClose() thay vì getAllItems().
        // Truy vấn có WHERE lọc sẵn trong DB → chỉ trả về phiên cần đóng,
        // tránh tải toàn bộ bảng items mỗi giây.
        long now = System.currentTimeMillis();
        List<Item> expiredItems = itemDAO.getRunningItemsToClose(now);

        for (Item item : expiredItems) {
            // Dùng per-item lock thay vì synchronized(this):
            // Scheduler chỉ cần khóa item đang đóng, không ảnh hưởng item khác.
            synchronized (getLock(item.getId())) {
                Item fresh = itemDAO.getItemById(item.getId());
                if (fresh == null || fresh.getStatus() != Item.Status.RUNNING) continue;

                itemDAO.updateStatus(item.getId(), Item.Status.FINISHED);

                // Dọn auto-bids và lock của phiên đã kết thúc (giải phóng bộ nhớ)
                autoBids.remove(item.getId());
                itemLocks.remove(item.getId());

                String winner     = fresh.getCurrentHighestBidder();
                boolean hasWinner = winner != null && !winner.equals("Chưa có");

                String endMsg = "Phiên #" + fresh.getId()
                        + " (" + fresh.getName() + ") đã kết thúc!\n"
                        + (hasWinner
                        ? "Người thắng: " + winner + " | Giá: $" + fresh.getCurrentHighestBid()
                        : "Không có người tham gia.");

                server.broadcast(new Message("AUCTION_ENDED", endMsg));
                System.out.println("--- GÕ BÚA! " + fresh.getName()
                        + (hasWinner ? " | Winner: " + winner + " | $" + fresh.getCurrentHighestBid()
                        : " | Không ai đặt giá") + " ---");
            }
        }
    }

    // =========================================================================
    // LẤY DANH SÁCH PHIÊN CHO DASHBOARD
    // =========================================================================
    public List<Item> getActiveAuctions() {
        // getDashboardItems() trả về tất cả trừ CANCELED (kể cả FINISHED)
        // để Dashboard hiển thị kết quả phiên vừa kết thúc.
        return itemDAO.getActiveItems();
    }

    // NOTE: placeBid() đã bị xóa (dead code).
    // ClientHandler gọi trực tiếp BidDAO.placeBidTransaction() để có
    // Pessimistic Locking + transaction an toàn, sau đó gọi handlePostBidSuccess().

    // =========================================================================
    // SAU KHI BidDAO.placeBidTransaction() THÀNH CÔNG
    // Được gọi từ ClientHandler — lo Anti-sniping + Broadcast + Auto-bid
    // =========================================================================
    public void handlePostBidSuccess(int itemId, String username,
                                     double bidAmount, AuctionServer auctionServer) {
        Item currentItem = itemDAO.getItemById(itemId);
        if (currentItem == null) return;

        // Anti-sniping (đã có per-item lock bên trong)
        applyAntiSniping(currentItem);

        currentItem.setCurrentHighestBid(bidAmount);
        currentItem.setCurrentHighestBidder(username);
        auctionServer.broadcastToItemWatchers(itemId, new Message("BID_UPDATE", gson.toJson(currentItem)));

        // Kích hoạt auto-bid ASYNC — không block luồng ClientHandler chờ đợi.
        // Lý do: triggerAutoBids có thể chạy tới MAX_AUTO_ROUNDS=50 vòng,
        // mỗi vòng là 1 lần gọi DB (~50-200ms) → tổng cộng vài giây.
        // Nếu gọi đồng bộ ở đây, client phải chờ toàn bộ chuỗi auto-bid
        // mới nhận được phản hồi "SUCCESS" cho bid của chính mình.
        // Giải pháp: dispatch sang ForkJoinPool.commonPool() (giống handleAutoBid).
        // Kết quả từng vòng auto-bid vẫn được broadcast đến watcher qua BID_UPDATE.
        CompletableFuture.runAsync(() ->
                        triggerAutoBids(itemId, username, bidAmount, auctionServer))
                .exceptionally(ex -> {
                    System.err.println("[SERVICE] Lỗi async triggerAutoBids item #"
                            + itemId + ": " + ex.getMessage());
                    return null;
                });
    }

    // =========================================================================
    // AUTO-BIDDING — Đăng ký
    //
    // FIX: Dùng synchronized(getLock(itemId)) thay vì synchronized(this).
    // Lý do: chỉ cần bảo vệ thao tác trên danh sách của itemId này,
    // không cần khóa cả class khi đăng ký cho item khác.
    // =========================================================================
    public void registerAutoBid(int itemId, String username,
                                double maxBid, double increment) {
        synchronized (getLock(itemId)) {
            List<AutoBidEntry> list = autoBids.computeIfAbsent(itemId, k -> new ArrayList<>());

            list.removeIf(e -> e.username.equals(username));
            list.add(new AutoBidEntry(username, maxBid, increment));
            list.sort(Comparator.comparingLong(e -> e.registeredAt));

            System.out.printf("[AUTO-BID] Đăng ký: %s | item #%d | max=$%.2f | inc=$%.2f%n",
                    username, itemId, maxBid, increment);
        }
    }

    // =========================================================================
    // AUTO-BIDDING — Hủy đăng ký
    // =========================================================================
    public void cancelAutoBid(int itemId, String username) {
        synchronized (getLock(itemId)) {
            List<AutoBidEntry> list = autoBids.get(itemId);
            if (list != null) {
                boolean removed = list.removeIf(e -> e.username.equals(username));
                if (removed) {
                    System.out.printf("[AUTO-BID] Đã hủy: %s | item #%d%n", username, itemId);
                }
            }
        }
    }

    // =========================================================================
    // AUTO-BIDDING — Kích hoạt vòng lặp đấu giá tự động
    //
    // FIX QUAN TRỌNG: Tách "đọc danh sách" (cần lock) ra khỏi "gọi DB" (không cần lock).
    //
    // Logic từng vòng:
    //   Bước 1 — Trong lock: snapshot danh sách, tìm candidate tiếp theo.
    //             Lock giữ ngắn (chỉ duyệt ArrayList trong RAM, microseconds).
    //   Bước 2 — Ngoài lock: gọi bidDAO.placeBidTransaction() (I/O MySQL).
    //             Các thread khác có thể registerAutoBid/cancelAutoBid trong thời gian này.
    //   Bước 3 — Ngoài lock: broadcast kết quả, applyAntiSniping.
    //
    // Tại sao an toàn dù không giữ lock trong Bước 2?
    //   BidDAO.placeBidTransaction() đã có Pessimistic Lock (SELECT FOR UPDATE)
    //   ở tầng DB → chỉ 1 bid thắng dù nhiều thread cùng cố ghi.
    //   Nếu DB từ chối bid (giá thấp hơn), vòng lặp kết thúc tự nhiên.
    // =========================================================================
    private static final int MAX_AUTO_ROUNDS = 50;

    public void triggerAutoBids(int itemId, String currentWinner,
                                double currentBid, AuctionServer auctionServer) {
        String winner = currentWinner;
        double price  = currentBid;

        for (int round = 0; round < MAX_AUTO_ROUNDS; round++) {

            // ── BƯỚC 1: Tìm candidate — giữ lock ngắn, không có I/O ──────────
            final AutoBidEntry candidate;
            final double nextBid;

            synchronized (getLock(itemId)) {
                List<AutoBidEntry> list = autoBids.get(itemId);
                if (list == null || list.isEmpty()) break;

                AutoBidEntry found = null;
                for (AutoBidEntry entry : list) {
                    if (entry.username.equals(winner)) continue;
                    double proposed = price + entry.increment;
                    if (proposed <= entry.maxBid) {
                        found = entry;
                        break;
                    }
                }
                if (found == null) break;

                candidate = found;
                nextBid   = price + candidate.increment;
            }
            // Lock đã được giải phóng trước khi gọi DB

            // ── BƯỚC 2: Đặt giá — I/O DB, KHÔNG giữ lock ────────────────────
            String result = bidDAO.placeBidTransaction(itemId, candidate.username, nextBid);

            // ── BƯỚC 3: Xử lý kết quả ────────────────────────────────────────
            if ("SUCCESS".equals(result)) {
                winner = candidate.username;
                price  = nextBid;

                Item updated = itemDAO.getItemById(itemId);
                if (updated == null) break;

                // applyAntiSniping cũng dùng per-item lock bên trong
                applyAntiSniping(updated);

                updated.setCurrentHighestBid(price);
                updated.setCurrentHighestBidder(winner);
                auctionServer.broadcastToItemWatchers(itemId,
                        new Message("BID_UPDATE", gson.toJson(updated)));

                System.out.printf("[AUTO-BID] Round %d: %s đặt $%.2f cho item #%d%n",
                        round + 1, winner, price, itemId);
            } else {
                // DB từ chối (phiên đã đóng, giá không còn hợp lệ, v.v.)
                System.err.println("[AUTO-BID] placeBidTransaction trả lỗi: " + result);
                break;
            }
        }
    }

    // =========================================================================
    // HELPER — Anti-sniping: nếu còn < 60 giây thì gia hạn thêm 5 phút
    //
    // FIX RACE CONDITION:
    //   Phiên bản cũ (không synchronized):
    //     Thread A đọc remaining=55s → vào if → chưa kịp updateEndTime
    //     Thread B đọc remaining=55s → vào if → cả 2 gia hạn → +10 phút
    //
    //   Phiên bản mới (synchronized trên per-item lock):
    //     Thread A lấy lock → re-read end_time từ DB → remaining=55s → gia hạn → giải phóng lock
    //     Thread B lấy lock → re-read end_time từ DB → remaining=305s → KHÔNG gia hạn → giải phóng lock
    //
    //   Tại sao cần re-read trong DB (itemDAO.getItemById)?
    //     end_time trong object `item` được truyền vào là snapshot tại thời điểm
    //     bid xảy ra. Thread B có thể đọc snapshot cũ trước khi Thread A cập nhật.
    //     Re-read đảm bảo lấy giá trị mới nhất từ DB.
    // =========================================================================
    private void applyAntiSniping(Item item) {
        if (item.getEndTime() <= 0) return;

        synchronized (getLock(item.getId())) {
            // Re-read từ DB để lấy end_time mới nhất (thread khác có thể đã gia hạn rồi)
            Item fresh = itemDAO.getItemById(item.getId());
            if (fresh == null || fresh.getStatus() != Item.Status.RUNNING) return;

            long remaining = fresh.getEndTime() - System.currentTimeMillis();
            if (remaining > 0 && remaining < 60_000) {
                long newEndTime = fresh.getEndTime() + 5 * 60_000L;
                itemDAO.updateEndTime(item.getId(), newEndTime);

                // Cập nhật object được truyền vào để caller thấy end_time mới
                item.setEndTime(newEndTime);

                String payload = gson.toJson(new TimeExtendedPayload(item.getId(), newEndTime));
                server.broadcastToItemWatchers(item.getId(),
                        new Message("TIME_EXTENDED", payload));
                System.out.println("[ANTI-SNIPING] Gia hạn +5 phút cho item #" + item.getId());
            }
        }
    }

    // =========================================================================
    // DỌN DẸP khi Server tắt
    // =========================================================================
    public void shutdown() {
        // Bước 1: Yêu cầu scheduler dừng nhận task mới.
        scheduler.shutdown();

        // Bước 2: Chờ task đang chạy (refreshAuctionsStatus) hoàn thành.
        // Quan trọng: phải chờ TRƯỚC khi xóa autoBids/itemLocks và TRƯỚC khi
        // AuctionServer đóng DB pool. Nếu không, refreshAuctionsStatus đang
        // gọi itemDAO giữa chừng sẽ gặp SQLException vì pool đã bị đóng.
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                // Hết 5 giây mà task vẫn chưa xong → ép dừng ngay
                scheduler.shutdownNow();
                System.out.println("[SERVICE] Ép dừng scheduler sau 5 giây chờ.");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt(); // khôi phục trạng thái interrupt
        }

        // Bước 3: Dọn dẹp bộ nhớ sau khi scheduler đã thực sự dừng
        autoBids.clear();
        itemLocks.clear();
        System.out.println("[SERVICE] AuctionService đã dừng.");
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    public static class AutoBidEntry {
        final String username;
        final double maxBid;
        final double increment;
        final long   registeredAt;

        AutoBidEntry(String username, double maxBid, double increment) {
            this.username     = username;
            this.maxBid       = maxBid;
            this.increment    = increment;
            this.registeredAt = System.currentTimeMillis();
        }
    }

    private static class TimeExtendedPayload {
        int  itemId;
        long newEndTime;

        TimeExtendedPayload(int itemId, long newEndTime) {
            this.itemId     = itemId;
            this.newEndTime = newEndTime;
        }
    }

    /** Yêu cầu nạp tiền của Bidder/Seller gửi lên Admin. */
    public static class DepositRequest {
        public int    id;
        public String username;
        public double amount;
        public String status;    // PENDING | APPROVED | REJECTED
        public long   createdAt;

        DepositRequest(int id, String username, double amount) {
            this.id        = id;
            this.username  = username;
            this.amount    = amount;
            this.status    = "PENDING";
            this.createdAt = System.currentTimeMillis();
        }
    }
}