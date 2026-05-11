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

    private ItemDAO itemDAO;
    private AuctionServer server;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AuctionService(AuctionServer server) {
        this.itemDAO = new ItemDAO();
        this.server = server;
        startScheduler();
        System.out.println("[SERVICE] AuctionService đã sẵn sàng.");
    }

    // ========== SCHEDULER TỰ ĐỘNG ==========

    public void startScheduler() {
        scheduler.scheduleAtFixedRate(this::refreshAuctionsStatus, 0, 1, TimeUnit.SECONDS);
        System.out.println("[SERVICE] Scheduler kiểm tra hết hạn đã chạy (mỗi 1 giây).");
    }

    // ========== LẤY DANH SÁCH ==========

    public List<Item> getActiveAuctions() {
        return itemDAO.getActiveItems();
    }

    // ========== XỬ LÝ ĐẶT GIÁ (CORE) ==========

    /**
     * Hàm xử lý khi người dùng đặt giá.
     * synchronized: đảm bảo chỉ 1 thread đặt giá cho 1 item tại 1 thời điểm.
     */
    public synchronized boolean placeBid(int itemId, String username, double bidAmount) throws Exception {

        // 1. Lấy item từ database
        Item currentItem = itemDAO.getItemById(itemId);

        if (currentItem == null) {
            throw new Exception("Không tìm thấy phiên đấu giá #" + itemId);
        }

        // 2. Kiểm tra trạng thái: chỉ đấu giá khi RUNNING
        if (currentItem.getStatus() != Item.Status.RUNNING) {
            throw new Exception("Phiên đấu giá không hoạt động (trạng thái: " + currentItem.getStatus() + ")");
        }

        // 3. Kiểm tra hết hạn
        long now = System.currentTimeMillis();
        if (currentItem.getEndTime() > 0 && now > currentItem.getEndTime()) {
            currentItem.setStatus(Item.Status.FINISHED);
            itemDAO.updateStatus(itemId, Item.Status.FINISHED);
            throw new Exception("Phiên đấu giá đã kết thúc!");
        }

        // 4. Kiểm tra giá đặt > giá hiện tại
        double currentMaxPrice = currentItem.getCurrentHighestBid();
        if (bidAmount <= currentMaxPrice) {
            throw new Exception("Giá đặt ($" + bidAmount + ") phải cao hơn giá hiện tại ($" + currentMaxPrice + ")!");
        }

        // 5. ANTI-SNIPING: nếu còn dưới 60 giây, gia hạn thêm 5 phút
        if (currentItem.getEndTime() > 0) {
            long remainingMs = currentItem.getEndTime() - now;
            if (remainingMs < 60_000 && remainingMs > 0) {
                long newEndTime = currentItem.getEndTime() + (5 * 60_000); // +5 phút
                itemDAO.updateEndTime(itemId, newEndTime);
                currentItem.setEndTime(newEndTime);
                System.out.println("[ANTI-SNIPING] Gia hạn thêm 5 phút cho item #" + itemId);
            }
        }

        // 6. Cập nhật giá mới vào database
        boolean success = itemDAO.placeBid(String.valueOf(itemId), bidAmount, username);

        if (success) {
            // Cập nhật lại object trong RAM
            currentItem.setCurrentHighestBid(bidAmount);
            currentItem.setCurrentHighestBidder(username);

            // 7. BROADCAST: gửi cập nhật cho TẤT CẢ client
            Message updateMsg = new Message("BID_UPDATE", new Gson().toJson(currentItem));
            server.broadcast(updateMsg);

            System.out.println("[BID] " + username + " đặt $" + bidAmount + " cho item #" + itemId);
            return true;
        } else {
            throw new Exception("Lỗi hệ thống khi cập nhật database!");
        }
    }

    // ========== TỰ ĐỘNG ĐÓNG PHIÊN HẾT HẠN ==========

    public void refreshAuctionsStatus() {
        long now = System.currentTimeMillis();
        List<Item> allItems = itemDAO.getAllItems();

        for (Item item : allItems) {
            if (item.getStatus() == Item.Status.RUNNING
                    && item.getEndTime() > 0
                    && now > item.getEndTime()) {

                synchronized (this) {
                    // Cập nhật trạng thái FINISHED
                    item.setStatus(Item.Status.FINISHED);
                    itemDAO.updateStatus(item.getId(), Item.Status.FINISHED);

                    // Tạo thông báo kết quả
                    String endMsg = "Phiên #" + item.getId() + " (" + item.getName() + ") đã kết thúc!\n"
                            + "Người thắng: " + item.getCurrentHighestBidder()
                            + " | Giá: $" + item.getCurrentHighestBid();

                    Message endMessage = new Message("AUCTION_ENDED", endMsg);
                    server.broadcast(endMessage);

                    System.out.println("--- GÕ BÚA! " + item.getName()
                            + " | Winner: " + item.getCurrentHighestBidder()
                            + " | $" + item.getCurrentHighestBid() + " ---");
                }
            }
        }
    }

    // ========== DỌN DẸP ==========

    public void shutdown() {
        scheduler.shutdown();
        System.out.println("[SERVICE] AuctionService đã dừng.");
    }
}