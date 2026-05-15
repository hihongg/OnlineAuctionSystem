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
    private final BidDAO   bidDAO;      // Dùng cho auto-bid (placeBidTransaction)
    private final AuctionServer server;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Gson gson = new Gson();

    // =========================================================================
    // AUTO-BID STORAGE
    //
    // Key  = itemId
    // Value = danh sách AutoBidEntry, sắp xếp theo registeredAt tăng dần
    //         (người đăng ký sớm hơn được ưu tiên khi hòa maxBid)
    //
    // ConcurrentHashMap vì nhiều ClientHandler thread đồng thời gọi registerAutoBid.
    // Các thao tác modify list bên trong được bọc synchronized riêng.
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
    //   1. Kích hoạt phiên OPEN đã đến giờ → RUNNING
    //   2. Đóng phiên RUNNING đã hết giờ → FINISHED + broadcast kết quả
    // =========================================================================
    public void startScheduler() {
        scheduler.scheduleAtFixedRate(this::refreshAuctionsStatus, 0, 1, TimeUnit.SECONDS);
        System.out.println("[SERVICE] Scheduler đã chạy (kiểm tra mỗi 1 giây).");
    }

    public void refreshAuctionsStatus() {
        // Bước 1: OPEN → RUNNING
        itemDAO.activatePendingItems();

        // Bước 2: RUNNING → FINISHED
        long now = System.currentTimeMillis();
        List<Item> allItems = itemDAO.getAllItems();

        for (Item item : allItems) {
            if (item.getStatus() == Item.Status.RUNNING
                    && item.getEndTime() > 0
                    && now > item.getEndTime()) {

                synchronized (this) {
                    Item fresh = itemDAO.getItemById(item.getId());
                    if (fresh == null || fresh.getStatus() != Item.Status.RUNNING) continue;

                    itemDAO.updateStatus(item.getId(), Item.Status.FINISHED);

                    // Dọn auto-bids của phiên đã kết thúc (giải phóng bộ nhớ)
                    autoBids.remove(item.getId());

                    String winner   = item.getCurrentHighestBidder();
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
    }

    // =========================================================================
    // LẤY DANH SÁCH PHIÊN ĐANG HOẠT ĐỘNG
    // =========================================================================
    public List<Item> getActiveAuctions() {
        return itemDAO.getActiveItems();
    }

    // =========================================================================
    // ĐẶT GIÁ QUA SERVICE (giữ cho tương thích — ClientHandler ưu tiên BidDAO)
    // =========================================================================
    public synchronized boolean placeBid(int itemId, String username, double bidAmount) throws Exception {
        Item currentItem = itemDAO.getItemById(itemId);
        if (currentItem == null)
            throw new Exception("Không tìm thấy phiên đấu giá #" + itemId);

        if (currentItem.getStatus() != Item.Status.RUNNING)
            throw new Exception("Phiên đấu giá không hoạt động (trạng thái: " + currentItem.getStatus() + ")");

        long now = System.currentTimeMillis();
        if (currentItem.getEndTime() > 0 && now > currentItem.getEndTime()) {
            itemDAO.updateStatus(itemId, Item.Status.FINISHED);
            throw new Exception("Phiên đấu giá đã kết thúc!");
        }

        if (bidAmount <= currentItem.getCurrentHighestBid())
            throw new Exception("Giá đặt ($" + bidAmount + ") phải cao hơn giá hiện tại ($"
                    + currentItem.getCurrentHighestBid() + ")!");

        applyAntiSniping(currentItem);
        boolean success = itemDAO.placeBidById(itemId, bidAmount, username);

        if (success) {
            currentItem.setCurrentHighestBid(bidAmount);
            currentItem.setCurrentHighestBidder(username);
            server.broadcastToItemWatchers(itemId, new Message("BID_UPDATE", gson.toJson(currentItem)));
            System.out.println("[BID] " + username + " đặt $" + bidAmount + " cho item #" + itemId);
            return true;
        }
        throw new Exception("Lỗi hệ thống khi cập nhật database!");
    }

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

        // Cập nhật giá trong object và chỉ gửi đến client đang xem item này
        currentItem.setCurrentHighestBid(bidAmount);
        currentItem.setCurrentHighestBidder(username);
        auctionServer.broadcastToItemWatchers(itemId, new Message("BID_UPDATE", gson.toJson(currentItem)));

        // Kích hoạt auto-bid ngay sau khi có bid mới
        triggerAutoBids(itemId, username, bidAmount, auctionServer);
    }

    // =========================================================================
    // AUTO-BIDDING — Đăng ký
    //
    // Nếu user đã có auto-bid trên phiên này → cập nhật (overwrite).
    // =========================================================================
    public synchronized void registerAutoBid(int itemId, String username,
                                             double maxBid, double increment) {
        List<AutoBidEntry> list = autoBids.computeIfAbsent(itemId, k -> new ArrayList<>());

        // Xóa auto-bid cũ của cùng user (update)
        list.removeIf(e -> e.username.equals(username));

        // Thêm entry mới (registeredAt = now → được sắp xếp đúng ưu tiên)
        list.add(new AutoBidEntry(username, maxBid, increment));

        // Sắp xếp theo thời gian đăng ký tăng dần (người đăng ký sớm ưu tiên)
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
    //
    // Thuật toán:
    //   Lặp qua các AutoBidEntry theo thứ tự ưu tiên (registeredAt sớm nhất):
    //     - Bỏ qua người đang dẫn đầu (currentWinner) — họ không cần tự đấu giá
    //     - Tìm người đầu tiên CÓ THỂ ra giá: nextBid = currentBid + increment ≤ maxBid
    //     - Đặt bid qua BidDAO (có transaction lock — an toàn với concurrency)
    //     - Nếu thành công: broadcast, lặp lại để kiểm tra người khác có counter không
    //     - Vòng lặp dừng khi không còn ai có thể ra giá cao hơn
    //
    // MAX_AUTO_ROUNDS: giới hạn tối đa số vòng để tránh vòng lặp vô tận
    //   (ví dụ: 2 người cùng có auto-bid maxBid bằng nhau)
    // =========================================================================
    private static final int MAX_AUTO_ROUNDS = 50;

    public synchronized void triggerAutoBids(int itemId, String currentWinner,
                                             double currentBid, AuctionServer auctionServer) {
        List<AutoBidEntry> list = autoBids.get(itemId);
        if (list == null || list.isEmpty()) return;

        String  winner = currentWinner;
        double  price  = currentBid;

        for (int round = 0; round < MAX_AUTO_ROUNDS; round++) {
            AutoBidEntry candidate = null;

            // Tìm ứng viên đủ điều kiện đầu tiên (theo thứ tự đăng ký)
            for (AutoBidEntry entry : list) {
                if (entry.username.equals(winner)) continue;          // người dẫn đầu bỏ qua
                double nextBid = price + entry.increment;
                if (nextBid <= entry.maxBid) {
                    candidate = entry;
                    break;
                }
            }

            if (candidate == null) break; // Không ai đủ điều kiện → kết thúc

            double nextBid = price + candidate.increment;
            String result  = bidDAO.placeBidTransaction(itemId, candidate.username, nextBid);

            if ("SUCCESS".equals(result)) {
                winner = candidate.username;
                price  = nextBid;

                // Anti-sniping cho auto-bid
                Item updated = itemDAO.getItemById(itemId);
                if (updated == null) break;
                applyAntiSniping(updated);

                // Broadcast giá mới chỉ đến client đang xem item này
                updated.setCurrentHighestBid(price);
                updated.setCurrentHighestBidder(winner);
                auctionServer.broadcastToItemWatchers(itemId, new Message("BID_UPDATE", gson.toJson(updated)));

                System.out.printf("[AUTO-BID] Round %d: %s đặt $%.2f cho item #%d%n",
                        round + 1, winner, price, itemId);
            } else {
                // Lỗi DB hoặc phiên đã đóng → dừng
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

            // Thông báo gia hạn chỉ đến client đang xem item này
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

    /**
     * Thông tin một đăng ký auto-bid.
     * registeredAt dùng để sắp xếp ưu tiên: người đăng ký SỚM HƠN thắng khi hòa.
     */
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

    /** Payload JSON cho broadcast TIME_EXTENDED */
    private static class TimeExtendedPayload {
        int  itemId;
        long newEndTime;

        TimeExtendedPayload(int itemId, long newEndTime) {
            this.itemId     = itemId;
            this.newEndTime = newEndTime;
        }
    }
}