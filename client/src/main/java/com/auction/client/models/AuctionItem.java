package com.auction.client.models;

public class AuctionItem {
    private String name;
    private double currentBid;
    private String timeLeft;

    public AuctionItem(String name, double currentBid, String timeLeft) {
        this.name = name;
        this.currentBid = currentBid;
        this.timeLeft = timeLeft;
    }

    public String getName() { return name; }
    public double getCurrentBid() { return currentBid; }
    public String getTimeLeft() { return timeLeft; }
}