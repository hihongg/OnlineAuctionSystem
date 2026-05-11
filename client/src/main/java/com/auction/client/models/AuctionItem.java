//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.auction.client.models;

import java.time.LocalDateTime;

public class AuctionItem {
    private String name;
    private double currentBid;
    private LocalDateTime endTime;

    public AuctionItem(String name, double currentBid, LocalDateTime endTime) {
        this.name = name;
        this.currentBid = currentBid;
        this.endTime = endTime;
    }

    public String getName() {
        return this.name;
    }

    public double getCurrentBid() {
        return this.currentBid;
    }

    public LocalDateTime getEndTime() {
        return this.endTime;
    }
}
