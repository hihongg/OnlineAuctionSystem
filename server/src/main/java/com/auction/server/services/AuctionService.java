package com.auction.server.services;

import com.auction.server.dao.ItemDAO;
import com.auction.shared.models.Item; // Dùng model Item chuẩn từ CSDL
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

public class AuctionService {
    // THAY ĐỔI LỚN NHẤT: Bỏ HashMap đi, dùng ItemDAO để móc thẳng vào MySQL
    private ItemDAO itemDAO;

    public AuctionService() {
        this.itemDAO = new ItemDAO();
    }

    // --- CÁC HÀM QUẢN LÝ CƠ BẢN ---
    // Thành viên 3 (Mạng) sẽ gọi hàm này để lấy danh sách gửi về cho Client
    public List<Item> getActiveAuctions() {
        return itemDAO.getAllOpenItems();
    }

    // --- THUẬT TOÁN ĐẶT GIÁ ĐỒNG THỜI ---
    /**
     * Hàm xử lý khi có người bấm nút Đặt giá.
     * Để dễ truyền qua mạng Socket, ta nhận vào Tên sản phẩm và Tên user (dạng String).
     */
    public synchronized boolean placeBid(String itemName, String username, double bidAmount) throws Exception {
        // 1. Lấy thông tin mới nhất của sản phẩm TỪ DATABASE
        Item currentItem = itemDAO.getItemByName(itemName);

        if (currentItem == null) {
            throw new Exception("Lỗi: Không tìm thấy phiên đấu giá này trong hệ thống!");
        }

        // 2. Kiểm tra Trạng thái: Chỉ cho phép đặt giá khi đang OPEN
        if (!"OPEN".equals(currentItem.getStatus())) {
            throw new Exception("Thất bại: Phiên đấu giá chưa mở hoặc đã kết thúc!");
        }

        // 3. Kiểm tra tính hợp lệ của số tiền
        double currentMaxPrice = currentItem.getCurrentPrice();
        if (bidAmount <= currentMaxPrice) {
            throw new Exception("Thất bại: Giá đặt (" + bidAmount + "$) phải cao hơn giá hiện tại (" + currentMaxPrice + "$)!");
        }

        // --- BẮT ĐẦU LOGIC ANTI-SNIPING CỦA BẠN (Cực xịn) ---
        /* LƯU Ý: Để dùng được đoạn này, trong bảng 'items' ở MySQL
           bạn phải có thêm cột 'end_time' kiểu DATETIME nhé */
        if (currentItem.getEndTime() != null) {
            long minutesRemaining = Duration.between(LocalDateTime.now(), currentItem.getEndTime()).toMinutes();
            // Nếu thời gian còn lại dưới 1 phút mà có người đặt giá hợp lệ
            if (minutesRemaining < 1 && minutesRemaining >= 0) {
                // Tự động cộng thêm 5 phút vào thời gian kết thúc
                LocalDateTime newEndTime = currentItem.getEndTime().plusMinutes(5);

                // Gọi DAO để cập nhật giờ mới xuống CSDL
                itemDAO.updateEndTime(itemName, newEndTime);
                System.out.println("[Anti-sniping] Phút chót có người đặt giá! Gia hạn: " + itemName + " đến " + newEndTime);
            }
        }
        // --- KẾT THÚC LOGIC ANTI-SNIPING ---

        // 4. Nếu vượt qua mọi cửa ải -> Báo Thủ kho (ItemDAO) ghi nhận lượt đặt giá mới xuống MySQL
        boolean success = itemDAO.placeBid(itemName, bidAmount, username);

        if (success) {
            System.out.println("[Thành công] Người chơi " + username + " đã vươn lên dẫn đầu với mức giá " + bidAmount + "$");
            return true;
        } else {
            throw new Exception("Lỗi hệ thống khi cập nhật CSDL. Vui lòng thử lại!");
        }
    }

    // --- HÀM CẬP NHẬT TRẠNG THÁI (ĐỒNG HỒ CÁT) ---
    public void refreshAuctionsStatus() {
        LocalDateTime now = LocalDateTime.now();
        List<Item> allItems = itemDAO.getAllItems(); // Lấy tất cả lên để check

        for (Item item : allItems) {
            if ("OPEN".equals(item.getStatus()) && item.getEndTime() != null && now.isAfter(item.getEndTime())) {
                // Khóa luồng khi thay đổi trạng thái
                synchronized (this) {
                    itemDAO.updateStatus(item.getName(), "CLOSED");
                    System.out.println("--- GÕ BÚA! ĐÃ ĐÓNG PHIÊN: " + item.getName() + " ---");
                    System.out.println("=> Người thắng: " + item.getTopBidder() + " với giá " + item.getCurrentPrice() + "$");
                }
            }
        }
    }
}