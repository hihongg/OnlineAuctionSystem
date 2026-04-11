package com.auction.shared.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {
    private Item item;                  // Sản phẩm được đấu giá
    private Seller seller;              // Người tạo phiên đấu giá
    private LocalDateTime startTime;    // Thời gian bắt đầu
    private LocalDateTime endTime;      // Thời gian kết thúc
    private AuctionStatus status;       // Trạng thái (OPEN, CLOSED...)

    private BidTransaction currentHighestBid;       // Giá cao nhất hiện tại (Người dẫn đầu)
    private List<BidTransaction> bidHistory;        // Lịch sử tất cả các lượt trả giá

    public Auction(Item item, Seller seller, LocalDateTime startTime, LocalDateTime endTime) {
        super();
        this.item = item;
        this.seller = seller;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = AuctionStatus.PENDING; // Mặc định khi mới tạo là Chờ mở
        this.bidHistory = new ArrayList<>();
        this.currentHighestBid = null; // Chưa ai đặt giá
    }

    // --- TÍNH ĐÓNG GÓI CHẶT CHẼ ---
    // Thay vì cho phép lấy hẳn list ra để add(), ta tự viết hàm addBid() để kiểm soát
    public void addBid(BidTransaction newBid) {
        this.bidHistory.add(newBid);
        this.currentHighestBid = newBid; // Cập nhật người dẫn đầu
    }

    // Getters & Setters cơ bản
    public Item getItem() { return item; }
    public Seller getSeller() { return seller; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; } // Dùng cho thuật toán Anti-sniping sau này

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public BidTransaction getCurrentHighestBid() { return currentHighestBid; }

    // Trả về bản sao của danh sách để tránh bị bên ngoài thay đổi (Defensive copying)
    public List<BidTransaction> getBidHistory() {
        return new ArrayList<>(bidHistory);
    }
}