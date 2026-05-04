package com.auction.server.services;

import com.auction.shared.models.Auction;
import com.auction.shared.models.AuctionStatus;
import com.auction.shared.models.BidTransaction;
import com.auction.shared.models.Bidder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class AuctionService {
    // Sử dụng ConcurrentHashMap để an toàn tuyệt đối trong môi trường đa luồng (Multi-threading)
    private Map<String, Auction> auctionDatabase;

    public AuctionService() {
        this.auctionDatabase = new ConcurrentHashMap<>(); // Đã sửa
    }

    // CÁC HÀM QUẢN LÝ CƠ BẢN
    public void addAuction(Auction auction) {
        auctionDatabase.put(auction.getId(), auction);
    }

    public Auction getAuction(String auctionId) {
        return auctionDatabase.get(auctionId);
    }

    // THUẬT TOÁN ĐẶT GIÁ ĐỒNG THỜI
    public boolean placeBid(String auctionId, Bidder bidder, double bidAmount) throws Exception {
        Auction auction = auctionDatabase.get(auctionId);

        if (auction == null) {
            throw new Exception("Lỗi: Không tìm thấy phiên đấu giá này trong hệ thống!");
        }

        // Khóa đối tượng 'auction' lại. Đảm bảo an toàn luồng (Thread-safe)
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
                currentMaxPrice = auction.getItem().getStartingPrice();
            }

            if (bidAmount <= currentMaxPrice) {
                throw new Exception("Thất bại: Giá đặt (" + bidAmount + ") phải cao hơn giá hiện tại (" + currentMaxPrice + ")!");
            }

            // Đã tinh chỉnh Anti-sniping: Tính toán bằng GIÂY thay vì PHÚT
            long secondsRemaining = java.time.Duration.between(java.time.LocalDateTime.now(), auction.getEndTime()).getSeconds();

            // Nếu thời gian còn lại dưới 60 giây (1 phút) mà có người đặt giá hợp lệ
            if (secondsRemaining < 60 && secondsRemaining >= 0) {
                // Tự động cộng thêm 5 phút (300 giây) vào thời gian kết thúc
                java.time.LocalDateTime newEndTime = auction.getEndTime().plusMinutes(5);
                auction.setEndTime(newEndTime);
                System.out.println("[Anti-sniping] Phút chót có người đặt giá! Phiên đấu giá được gia hạn đến: " + newEndTime);
            }

            // 3. Nếu vượt qua mọi cửa ải -> Ghi nhận lượt đặt giá mới
            BidTransaction newBid = new BidTransaction(bidder, bidAmount);
            auction.addBid(newBid);

            System.out.println("[Thành công] Người chơi " + bidder.getUsername() +
                    " đã vươn lên dẫn đầu với mức giá " + bidAmount);

            // [TODO cho Thành viên 3]: Ở đây gọi BidDAO để lưu 'newBid' xuống Database thật
            // [TODO cho Thành viên 3]: Ở đây gọi ClientHandler để Broadcast (Observer Pattern) gửi JSON báo giá mới cho mọi Client

            return true;
        }
    }

    public void refreshAuctionsStatus() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        for (Auction auction : auctionDatabase.values()) {
            // Nếu đang OPEN mà đã quá giờ kết thúc
            if (auction.getStatus() == AuctionStatus.OPEN && now.isAfter(auction.getEndTime())) {
                synchronized (auction) {
                    auction.setStatus(AuctionStatus.FINISHED); // Khuyến nghị đổi thành FINISHED theo tài liệu
                    System.out.println("--- KẾT THÚC PHIÊN: " + auction.getItem().getName() + " ---");
                    // [TODO]: Xác định người thắng cuộc (bidder có giá cao nhất)
                }
            }

            // Nếu đang PENDING mà đã đến giờ bắt đầu thì mở phiên
            if (auction.getStatus() == AuctionStatus.PENDING && now.isAfter(auction.getStartTime())) {
                synchronized (auction) {
                    auction.setStatus(AuctionStatus.OPEN); // Hoặc RUNNING
                    System.out.println("[Thông báo] Phiên đấu giá " + auction.getItem().getName() + " CHÍNH THỨC BẮT ĐẦU!");
                }
            }
        }
    }
}