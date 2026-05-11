package com.auction.shared.models;

import java.io.Serializable;

public class Item implements Serializable {

    public enum Status {
        OPEN,       // Vừa tạo, chưa bắt đầu
        RUNNING,    // Đang đấu giá
        FINISHED,   // Đã kết thúc
        PAID,       // Đã thanh toán
        CANCELED    // Đã hủy
    }

    private int id;
    private String name;
    private String description;
    private double startingPrice;
    private double currentHighestBid;
    private String currentHighestBidder;
    private Status status;
    private long endTime;           // timestamp milliseconds
    private int sellerId;

    // Constructor rỗng (cần cho Serialization)
    public Item() {}

    // Constructor 3 tham số (ItemDAO gọi)
    public Item(int id, String name, double startingPrice) {
        this.id = id;
        this.name = name;
        this.startingPrice = startingPrice;
        this.currentHighestBid = startingPrice;
        this.currentHighestBidder = "Chưa có";
        this.status = Status.OPEN;
    }

    // Constructor thêm sản phẩm mới
    public Item(String name, String description, double startingPrice, long endTime, int sellerId) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentHighestBid = startingPrice;
        this.currentHighestBidder = "Chưa có";
        this.status = Status.OPEN;
        this.endTime = endTime;
        this.sellerId = sellerId;
    }

    // ========== GETTER & SETTER ==========

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public double getCurrentHighestBid() { return currentHighestBid; }
    public void setCurrentHighestBid(double currentHighestBid) { this.currentHighestBid = currentHighestBid; }

    public String getCurrentHighestBidder() { return currentHighestBidder; }
    public void setCurrentHighestBidder(String currentHighestBidder) { this.currentHighestBidder = currentHighestBidder; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }
}