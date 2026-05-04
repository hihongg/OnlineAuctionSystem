package com.auction.shared.models;

import java.time.LocalDateTime; // Bắt buộc phải thêm dòng import này để xài thời gian

public class Item {
    private int id;
    private String name;
    private double startingPrice;
    private double currentPrice;
    private String topBidder;

    // --- 2 THUỘC TÍNH MỚI THÊM VÀO ---
    private String status = "OPEN"; // Trạng thái mặc định là đang mở bán
    private LocalDateTime endTime;  // Thời gian chốt phiên gõ búa

    // Constructor rỗng (Bắt buộc phải có để xài một số thư viện sau này)
    public Item() {}

    // Constructor dùng để lúc Thêm Sản Phẩm mới
    public Item(String name, double startingPrice) {
        this.name = name;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice; // Lúc mới thêm, giá hiện hành = giá khởi điểm
        this.topBidder = "Chưa có";
        this.status = "OPEN"; // Mới thêm vào thì tự động MỞ
    }

    // Constructor đầy đủ dùng để lấy dữ liệu từ MySQL nạp vào (Bản cũ)
    public Item(int id, String name, double startingPrice, double currentPrice, String topBidder) {
        this.id = id;
        this.name = name;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.topBidder = topBidder;
    }

    // --- Các hàm Getter và Setter ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public String getTopBidder() { return topBidder; }
    public void setTopBidder(String topBidder) { this.topBidder = topBidder; }

    // --- GETTER & SETTER CHO 2 THUỘC TÍNH MỚI ĐỂ AUCTION SERVICE XÀI ---
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}