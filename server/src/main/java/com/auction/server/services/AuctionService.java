package com.auction.server.services;

import com.auction.server.dao.ItemDAO;
import com.auction.shared.models.Item;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

public class AuctionService {

    // Kết nối thẳng với anh Thủ kho để móc dữ liệu từ MySQL
    private ItemDAO itemDAO;

    public AuctionService() {
        this.itemDAO = new ItemDAO();
    }

    // --- 1. HÀM LẤY DANH SÁCH ĐANG MỞ ---
    // (Thành viên 3 sẽ gọi hàm này để gửi list sản phẩm về cho Client hiển thị)
    public List<Item> getActiveAuctions() {
        return itemDAO.getActiveItems();
    }

    // --- 2. TRÙM CUỐI: THUẬT TOÁN ĐẶT GIÁ ĐỒNG THỜI ---
    /**
     * Hàm xử lý khi có người bấm nút Đặt giá.
     * Sử dụng 'throws Exception' để ném lỗi về cho Socket báo cho giao diện (Thành viên 1).
     */
    public synchronized boolean placeBid(String itemName, String username, double bidAmount) throws Exception {
        // 1. Kéo dữ liệu mới nhất của sản phẩm từ Database lên
        Item currentItem = itemDAO.getItemByName(itemName);

        if (currentItem == null) {
            throw new Exception("Lỗi: Không tìm thấy phiên đấu giá này trong hệ thống!");
        }

        // 2. Kiểm tra Trạng thái: Chỉ cho phép đặt giá khi đang OPEN
        if (!"OPEN".equals(currentItem.getStatus())) {
            throw new Exception("Thất bại: Phiên đấu giá chưa mở hoặc đã kết thúc!");
        }

        // 3. Kiểm tra tính hợp lệ của số tiền
        double currentMaxPrice = currentItem.getCurrentHighestBid();
        if (bidAmount <= currentMaxPrice) {
            throw new Exception("Thất bại: Giá đặt (" + bidAmount + "$) phải cao hơn giá hiện tại (" + currentMaxPrice + "$)!");
        }

        // 4. LUẬT ANTI-SNIPING (Chống bắn tỉa phút chót)
        if (currentItem.getEndTime() != null) {
            long minutesRemaining = Duration.between(LocalDateTime.now(), currentItem.getEndTime()).toMinutes();
            // Nếu thời gian còn lại dưới 1 phút mà có người đặt giá hợp lệ
            if (minutesRemaining < 1 && minutesRemaining >= 0) {
                // Tự động cộng thêm 5 phút vào thời gian kết thúc
                LocalDateTime newEndTime = currentItem.getEndTime().plusMinutes(5);
                itemDAO.updateEndTime(itemName, newEndTime);
                System.out.println("[Anti-sniping] Phút chót có người đặt giá! Gia hạn: " + itemName + " đến " + newEndTime);
            }
        }

        // 5. Vượt qua mọi cửa ải -> Báo Thủ kho ghi nhận lượt đặt giá mới xuống MySQL
        boolean success = itemDAO.placeBid(itemName, bidAmount, username);

        if (success) {
            System.out.println("[Thành công] Người chơi " + username + " đã vươn lên dẫn đầu với mức giá " + bidAmount + "$");
            return true;
        } else {
            throw new Exception("Lỗi hệ thống khi cập nhật CSDL. Vui lòng thử lại!");
        }
    }

    // --- 3. HÀM KIỂM TRA THỜI GIAN ĐỂ GÕ BÚA (ĐÓNG PHIÊN) ---
    public void refreshAuctionsStatus() {
        LocalDateTime now = LocalDateTime.now();
        List<Item> allItems = itemDAO.getAllItems();

        for (Item item : allItems) {
            // Nếu đang OPEN mà đã quá giờ kết thúc
            if ("OPEN".equals(item.getStatus()) && item.getEndTime() != null && now.isAfter(item.getEndTime())) {
                synchronized (this) {
                    // Update xuống DB là CLOSED
                    itemDAO.updateStatus(item.getName(), "CLOSED");
                    System.out.println("--- GÕ BÚA! ĐÃ ĐÓNG PHIÊN: " + item.getName() + " ---");
                    System.out.println("=> Người thắng: " + item.getCurrentHighestBidder() + " với giá " + item.getCurrentHighestBid() + "$");
                }
            }
        }
    }
}