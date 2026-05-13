package com.auction.server.services;

import com.auction.server.dao.ItemDAO;
import com.auction.server.network.AuctionServer;
import com.auction.shared.models.Item;
import com.auction.shared.models.Message;
import com.google.gson.Gson;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionService {

    private final ItemDAO itemDAO;
    private final AuctionServer server;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Gson gson = new Gson();

    public AuctionService(AuctionServer server) {
        this.itemDAO = new ItemDAO();
        this.server  = server;
        startScheduler();
        System.out.println("[SERVICE] AuctionService đã sẵn sàng.");
    }

    // =========================================================================
    // SCHEDULER — chạy mỗi 1 giây, xử lý 2 việc:
    //   1. Kích hoạt phiên OPEN đã đến giờ bắt đầu → RUNNING  (BUG 5 đã sửa)
    //   2. Đóng phiên RUNNING đã hết giờ → FINISHED + broadcast kết quả
    // =========================================================================
    public void startScheduler() {
        scheduler.scheduleAtFixedRate(this::refreshAuctionsStatus, 0, 1, TimeUnit.SECONDS);
        System.out.println("[SERVICE] Scheduler đã chạy (kiểm tra mỗi 1 giây).");
    }

    public void refreshAuctionsStatus() {
        // --- Bước 1: OPEN → RUNNING ---
        // FIX Bug 5: Trước đây scheduler không bao giờ kích hoạt phiên OPEN.
        // Seller đăng sản phẩm → status = OPEN → không bao giờ chuyển sang RUNNING
        // → Bidder không bao giờ thấy phiên đó dù start_time đã qua.
        itemDAO.activatePendingItems();

        // --- Bước 2: RUNNING → FINISHED ---
        long now = System.currentTimeMillis();
        List<Item> allItems = itemDAO.getAllItems();

        for (Item item : allItems) {
            if (item.getStatus() == Item.Status.RUNNING
                    && item.getEndTime() > 0
                    && now > item.getEndTime()) {

                synchronized (this) {
                    // Chỉ xử lý nếu DB vẫn còn RUNNING (tránh double-process)
                    Item fresh = itemDAO.getItemById(item.getId());
                    if (fresh == null || fresh.getStatus() != Item.Status.RUNNING) continue;

                    itemDAO.updateStatus(item.getId(), Item.Status.FINISHED);

                    String winner = item.getCurrentHighestBidder();
                    boolean hasWinner = winner != null && !winner.equals("Chưa có");

                    String endMsg = "Phiên #" + item.getId()
                            + " (" + item.getName() + ") đã kết thúc!\n"
                            + (hasWinner
                            ? "Người thắng: " + winner + " | Giá: $" + item.getCurrentHighestBid()
                            : "Không có người tham gia.");

                    server.broadcast(new Message("AUCTION_ENDED", endMsg));

                    System.out.println("--- GÕ BÚA! " + item.getName()
                            + (hasWinner ? " | Winner: " + winner + " | $" + item.getCurrentHighestBid() : " | Không có ai đặt giá") + " ---");
                }
            }
        }
    }

    // =========================================================================
    // LẤY DANH SÁCH PHIÊN ĐANG HOẠT ĐỘNG (dùng cho các handler cần lấy list)
    // =========================================================================
    public List<Item> getActiveAuctions() {
        return itemDAO.getActiveItems();
    }

    // =========================================================================
    // ĐẶT GIÁ — luồng đầy đủ qua Service (dùng khi không đi qua BidDAO trực tiếp)
    //
    // BUG CŨ (đã sửa): itemDAO.placeBid(String.valueOf(itemId), ...)
    //   → Truyền "123" (chuỗi số) vào WHERE name = ? → không tìm được item.
    //   FIX: Dùng itemDAO.placeBidById(itemId, ...) — WHERE id = ?
    //
    // Lưu ý: Trong luồng hiện tại, ClientHandler gọi BidDAO.placeBidTransaction()
    //   trực tiếp (an toàn hơn nhờ Transaction + Pessimistic Lock).
    //   Hàm này vẫn giữ lại để AuctionService có thể tái sử dụng nếu cần.
    // =========================================================================
    public synchronized boolean placeBid(int itemId, String username, double bidAmount) throws Exception {

        Item currentItem = itemDAO.getItemById(itemId);
        if (currentItem == null) {
            throw new Exception("Không tìm thấy phiên đấu giá #" + itemId);
        }

        if (currentItem.getStatus() != Item.Status.RUNNING) {
            throw new Exception("Phiên đấu giá không hoạt động (trạng thái: "
                    + currentItem.getStatus() + ")");
        }

        long now = System.currentTimeMillis();
        if (currentItem.getEndTime() > 0 && now > currentItem.getEndTime()) {
            itemDAO.updateStatus(itemId, Item.Status.FINISHED);
            throw new Exception("Phiên đấu giá đã kết thúc!");
        }

        if (bidAmount <= currentItem.getCurrentHighestBid()) {
            throw new Exception("Giá đặt ($" + bidAmount
                    + ") phải cao hơn giá hiện tại ($" + currentItem.getCurrentHighestBid() + ")!");
        }

        // Anti-sniping: nếu còn dưới 60 giây, gia hạn thêm 5 phút
        applyAntiSniping(currentItem);

        // FIX Bug 4: dùng placeBidById (WHERE id = ?) thay vì placeBid (WHERE name = ?)
        boolean success = itemDAO.placeBidById(itemId, bidAmount, username);

        if (success) {
            currentItem.setCurrentHighestBid(bidAmount);
            currentItem.setCurrentHighestBidder(username);
            server.broadcast(new Message("BID_UPDATE", gson.toJson(currentItem)));
            System.out.println("[BID] " + username + " đặt $" + bidAmount + " cho item #" + itemId);
            return true;
        } else {
            throw new Exception("Lỗi hệ thống khi cập nhật database!");
        }
    }

    // =========================================================================
    // SAU KHI BidDAO.placeBidTransaction() THÀNH CÔNG
    // Được gọi từ ClientHandler — chỉ lo Anti-sniping + Broadcast.
    // =========================================================================
    public void handlePostBidSuccess(int itemId, String username, double bidAmount, AuctionServer auctionServer) {
        Item currentItem = itemDAO.getItemById(itemId);
        if (currentItem == null) return;

        // Anti-sniping
        applyAntiSniping(currentItem);

        // Cập nhật RAM để broadcast đúng giá mới
        currentItem.setCurrentHighestBid(bidAmount);
        currentItem.setCurrentHighestBidder(username);

        // Broadcast cho tất cả client
        auctionServer.broadcast(new Message("BID_UPDATE", gson.toJson(currentItem)));
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
            System.out.println("[ANTI-SNIPING] Gia hạn +5 phút cho item #" + item.getId());
        }
    }

    // =========================================================================
    // DỌN DẸP khi Server tắt
    // =========================================================================
    public void shutdown() {
        scheduler.shutdown();
        System.out.println("[SERVICE] AuctionService đã dừng.");
    }
}