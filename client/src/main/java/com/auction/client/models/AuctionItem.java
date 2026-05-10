package com.auction.client.models;

import java.time.LocalDateTime;

public class AuctionItem {
    private String name;
    private double currentBid;
    private LocalDateTime endTime; // Đổi sang kiểu thời gian thực

    public AuctionItem(String name, double currentBid, LocalDateTime endTime) {
        this.name = name;
        this.currentBid = currentBid;
        this.endTime = endTime;
    }

    public String getName() { return name; }
    public double getCurrentBid() { return currentBid; }
    public LocalDateTime getEndTime() { return endTime; }
}