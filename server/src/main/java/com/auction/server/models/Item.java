package com.auction.server.models;

public class Item {
    private int id;
    private String name;
    private double startingPrice;
    private double currentHighestBid;
    private String currentHighestBidder;

    public Item(int id, String name, double startingPrice) {
        this.id = id;
        this.name = name;
        this.startingPrice = startingPrice;
        this.currentHighestBid = startingPrice; // Lúc mới tạo, giá cao nhất chính là giá khởi điểm
        this.currentHighestBidder = "Chưa có";
    }

    // Các phương thức Getters và Setters (Encapsulation)
    public int getId() { return id; }
    public String getName() { return name; }

    public double getCurrentHighestBid() { return currentHighestBid; }
    public void setCurrentHighestBid(double currentHighestBid) { this.currentHighestBid = currentHighestBid; }

    public String getCurrentHighestBidder() { return currentHighestBidder; }
    public void setCurrentHighestBidder(String currentHighestBidder) { this.currentHighestBidder = currentHighestBidder; }
}