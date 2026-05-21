package com.auction.client.models;

import java.time.LocalDateTime;

public class AuctionItem {
    private String name;
    private double currentBid;
    private LocalDateTime endTime;
    private String imagePath;   // THÊM MỚI
    private String description; // THÊM MỚI

    public AuctionItem(String name, double currentBid, LocalDateTime endTime, String imagePath, String description) {
        this.name = name;
        this.currentBid = currentBid;
        this.endTime = endTime;
        this.imagePath = imagePath;
        this.description = description;
    }

    public String getName() { return name; }
    public double getCurrentBid() { return currentBid; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getImagePath() { return imagePath; }
    public String getDescription() { return description; }

    public void setCurrentBid(double currentBid) { this.currentBid = currentBid; }
}