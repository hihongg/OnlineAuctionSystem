package com.auction.server.services;

import com.auction.shared.models.Auction;
import com.auction.shared.models.AuctionStatus;
import com.auction.shared.models.BidTransaction;
import com.auction.shared.models.Bidder;

import java.util.HashMap;
import java.util.Map;

public class AuctionService {
    // Mock Database: Lưu trữ tạm các phiên đấu giá trong RAM chờ Thành viên 3 làm xong Database thật
    private Map<String, Auction> auctionDatabase;

    public AuctionService() {
        this.auctionDatabase = new HashMap<>();
    }

    // --- CÁC HÀM QUẢN LÝ CƠ BẢN (GIAI ĐOẠN 2) ---
    public void addAuction(Auction auction) {
        auctionDatabase.put(auction.getId(), auction);
    }

    public Auction getAuction(String auctionId) {
        return auctionDatabase.get(auctionId);
    }

    // --- TRÙM CUỐI: THUẬT TOÁN ĐẶT GIÁ ĐỒNG THỜI (GIAI ĐOẠN 3) ---
    /**
     * Hàm xử lý khi có người bấm nút Đặt giá.
     * Sử dụng 'throws Exception' để ném lỗi về cho giao diện (Thành viên 1) hiển thị Popup.
     */
    public boolean placeBid(String auctionId, Bidder bidder, double bidAmount) throws Exception {
        Auction auction = auctionDatabase.get(auctionId);

        if (auction == null) {
            throw new Exception("Lỗi: Không tìm thấy phiên đấu giá này trong hệ thống!");
        }

        // TỪ KHÓA ĂN ĐIỂM CỦA GIẢNG VIÊN: synchronized
        // Khóa đối tượng 'auction' lại. Nếu 100 người cùng gọi hàm này,
        // luồng (thread) của họ sẽ phải xếp hàng chờ luồng trước chạy xong mới được vào.
        synchronized (auction) {

            // 1. Kiểm tra thời gian & Trạng thái: Chỉ cho phép đặt giá khi đang OPEN
            if (auction.getStatus() != AuctionStatus.OPEN) {
                throw new Exception("Thất bại: Phiên đấu giá chưa mở hoặc đã kết thúc!");
            }

            // 2. Kiểm tra tính hợp lệ của số tiền
            double currentMaxPrice = 0;
            if (auction.getCurrentHighestBid() != null) {
                currentMaxPrice = auction.getCurrentHighestBid().getBidAmount();
            } else {
                // Nếu chưa có ai đặt, giá phải lớn hơn hoặc bằng giá khởi điểm của sản phẩm
                currentMaxPrice = auction.getItem().getStartingPrice();
            }

            if (bidAmount <= currentMaxPrice) {
                throw new Exception("Thất bại: Giá đặt (" + bidAmount + ") phải cao hơn giá hiện tại (" + currentMaxPrice + ")!");
            }

            // 3. Nếu vượt qua mọi cửa ải -> Ghi nhận lượt đặt giá mới
            BidTransaction newBid = new BidTransaction(bidder, bidAmount);
            auction.addBid(newBid);

            System.out.println("[Thành công] Người chơi " + bidder.getUsername() +
                    " đã vươn lên dẫn đầu với mức giá " + bidAmount);

            // (Nâng cao - Chỗ này sau này Thành viên 3 sẽ gọi Socket để báo cho mọi người biết có giá mới)

            return true;
        }
    }
}