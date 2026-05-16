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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
            // Các item từ getRunningItemsToClose() đều đã RUNNING + end_time <= now
            synchronized (this) {
                Item fresh = itemDAO.getItemById(item.getId());
                if (fresh == null || fresh.getStatus() != Item.Status.RUNNING) continue;

                itemDAO.updateStatus(item.getId(), Item.Status.FINISHED);

                // Dọn auto-bids của phiên đã kết thúc (giải phóng bộ nhớ)
                autoBids.remove(item.getId());

                String winner     = item.getCurrentHighestBidder();
                boolean hasWinner = winner != null && !winner.equals("Chưa có");

                String endMsg = "Phiên #" + item.getId()
                        + " (" + item.getName() + ") đã kết thúc!\n"
                        + (hasWinner
                        ? "Người thắng: " + winner + " | Giá: $" + item.getCurrentHighestBid()
                        : "Không có người tham gia.");

                server.broadcast(new Message("AUCTION_ENDED", endMsg));
                System.out.println("--- GÕ BÚA! " + item.getName()
                        + (hasWinner ? " | Winner: " + winner + " | $" + item.getCurrentHighestBid()
                        : " | Không ai đặt giá") + " ---");
            }
        }
    }

    // =========================================================================
    // LẤY DANH SÁCH PHIÊN ĐANG HOẠT ĐỘNG
    // =========================================================================
    public List<Item> getActiveAuctions() {
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

        // Anti-sniping
        applyAntiSniping(currentItem);

        currentItem.setCurrentHighestBid(bidAmount);
        currentItem.setCurrentHighestBidder(username);
        auctionServer.broadcastToItemWatchers(itemId, new Message("BID_UPDATE", gson.toJson(currentItem)));

        // Kích hoạt auto-bid ngay sau khi có bid mới
        triggerAutoBids(itemId, username, bidAmount, auctionServer);
    }

    // =========================================================================
    // AUTO-BIDDING — Đăng ký
    // =========================================================================
    public synchronized void registerAutoBid(int itemId, String username,
                                             double maxBid, double increment) {
        List<AutoBidEntry> list = autoBids.computeIfAbsent(itemId, k -> new ArrayList<>());

        list.removeIf(e -> e.username.equals(username));
        list.add(new AutoBidEntry(username, maxBid, increment));
        list.sort(Comparator.comparingLong(e -> e.registeredAt));

        System.out.printf("[AUTO-BID] Đăng ký: %s | item #%d | max=$%.2f | inc=$%.2f%n",
                username, itemId, maxBid, increment);
    }

    // =========================================================================
    // AUTO-BIDDING — Hủy đăng ký
    // =========================================================================
    public synchronized void cancelAutoBid(int itemId, String username) {
        List<AutoBidEntry> list = autoBids.get(itemId);
        if (list != null) {
            boolean removed = list.removeIf(e -> e.username.equals(username));
            if (removed) {
                System.out.printf("[AUTO-BID] Đã hủy: %s | item #%d%n", username, itemId);
            }
        }
    }

    // =========================================================================
    // AUTO-BIDDING — Kích hoạt vòng lặp đấu giá tự động
    // =========================================================================
    private static final int MAX_AUTO_ROUNDS = 50;

    public synchronized void triggerAutoBids(int itemId, String currentWinner,
                                             double currentBid, AuctionServer auctionServer) {
        List<AutoBidEntry> list = autoBids.get(itemId);
        if (list == null || list.isEmpty()) return;

        String winner = currentWinner;
        double price  = currentBid;

        for (int round = 0; round < MAX_AUTO_ROUNDS; round++) {
            AutoBidEntry candidate = null;

            for (AutoBidEntry entry : list) {
                if (entry.username.equals(winner)) continue;
                double nextBid = price + entry.increment;
                if (nextBid <= entry.maxBid) {
                    candidate = entry;
                    break;
                }
            }

            if (candidate == null) break;

            double nextBid = price + candidate.increment;
            String result  = bidDAO.placeBidTransaction(itemId, candidate.username, nextBid);

            if ("SUCCESS".equals(result)) {
                winner = candidate.username;
                price  = nextBid;

                Item updated = itemDAO.getItemById(itemId);
                if (updated == null) break;
                applyAntiSniping(updated);

                updated.setCurrentHighestBid(price);
                updated.setCurrentHighestBidder(winner);
                auctionServer.broadcastToItemWatchers(itemId, new Message("BID_UPDATE", gson.toJson(updated)));

                System.out.printf("[AUTO-BID] Round %d: %s đặt $%.2f cho item #%d%n",
                        round + 1, winner, price, itemId);
            } else {
                System.err.println("[AUTO-BID] placeBidTransaction trả lỗi: " + result);
                break;
            }
        }
    }

    // =========================================================================
    // HELPER — Anti-sniping: nếu còn < 60 giây thì gia hạn thêm 5 phút
    // =========================================================================
    private void applyAntiSniping(Item item) {
        if (item.getEndTime() <= 0) return;
        long remaining = item.getEndTime() - System.currentTimeMillis();
        if (remaining > 0 && remaining < 60_000) {
            long newEndTime = item.getEndTime() + 5 * 60_000L;
            itemDAO.updateEndTime(item.getId(), newEndTime);
            item.setEndTime(newEndTime);

            String payload = gson.toJson(new TimeExtendedPayload(item.getId(), newEndTime));
            server.broadcastToItemWatchers(item.getId(), new Message("TIME_EXTENDED", payload));
            System.out.println("[ANTI-SNIPING] Gia hạn +5 phút cho item #" + item.getId());
        }
    }

    // =========================================================================
    // DỌN DẸP khi Server tắt
    // =========================================================================
    public void shutdown() {
        scheduler.shutdown();
        autoBids.clear();
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
}