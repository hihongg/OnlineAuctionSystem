package com.auction.shared.models; // Lưu ý: Nếu project của bạn dùng com.auction.server.models thì nhớ đổi lại dòng này nhé

import java.time.LocalDateTime;

public class Item {
    private int id;
    private String name;
    private double startingPrice;

    // Đã đổi tên 2 biến này để khớp với Database và DAO của bạn
    private double currentHighestBid;
    private String currentHighestBidder;

    private String status = "OPEN";
    private LocalDateTime endTime;

    // Constructor rỗng
    public Item() {}

    // Constructor 3 tham số (Bắt buộc phải có vì ItemDAO đang gọi nó)
    public Item(int id, String name, double startingPrice) {
        this.id = id;
        this.name = name;
        this.startingPrice = startingPrice;
        this.currentHighestBid = startingPrice; // Mới tạo thì giá cao nhất = giá khởi điểm
        this.currentHighestBidder = "Chưa có";
    }

    // Constructor dùng để lúc Thêm Sản Phẩm mới vào Server
    public Item(String name, double startingPrice) {
        this.name = name;
        this.startingPrice = startingPrice;
        this.currentHighestBid = startingPrice;
        this.currentHighestBidder = "Chưa có";
        this.status = "OPEN";
    }

    // --- Các hàm Getter và Setter ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    // Getter & Setter đã đổi tên cho chuẩn DAO
    public double getCurrentHighestBid() { return currentHighestBid; }
    public void setCurrentHighestBid(double currentHighestBid) { this.currentHighestBid = currentHighestBid; }

    public String getCurrentHighestBidder() { return currentHighestBidder; }
    public void setCurrentHighestBidder(String currentHighestBidder) { this.currentHighestBidder = currentHighestBidder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}