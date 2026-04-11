package com.auction.shared.models;

import java.time.LocalDateTime;

public class BidTransaction extends Entity {
    private Bidder bidder;        // Người đặt giá
    private double bidAmount;     // Số tiền đặt
    private LocalDateTime bidTime;// Thời điểm đặt

    public BidTransaction(Bidder bidder, double bidAmount) {
        super(); // Gọi Entity để tạo ID tự động
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.bidTime = LocalDateTime.now(); // Lấy giờ hệ thống lúc đặt
    }

    // Getters (Chỉ có Get, KHÔNG CÓ SET để đảm bảo tính bất biến - không ai được sửa lịch sử giá)
    public Bidder getBidder() { return bidder; }
    public double getBidAmount() { return bidAmount; }
    public LocalDateTime getBidTime() { return bidTime; }

    @Override
    public String toString() {
        return "BidTransaction{" +
                "bidder=" + bidder.getUsername() +
                ", bidAmount=" + bidAmount +
                ", time=" + bidTime +
                '}';
    }
}