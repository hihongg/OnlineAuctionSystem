package com.auction.shared.models;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class BidTransaction extends Entity {
    private Bidder bidder;        // Người đặt giá
    private double bidAmount;     // Số tiền đặt

    // FIX: Thay LocalDateTime bằng long (epoch ms) để Gson serialize được.
    // LocalDateTime không serialize được với Gson mặc định trên Java 9+.
    // bidTimeMs là epoch milliseconds — dễ truyền qua mạng, dễ hiển thị ở client.
    private long bidTimeMs;

    public BidTransaction(Bidder bidder, double bidAmount) {
        super(); // Gọi Entity để tạo ID tự động
        this.bidder    = bidder;
        this.bidAmount = bidAmount;
        this.bidTimeMs = System.currentTimeMillis();
    }

    // Getters (Chỉ có Get, KHÔNG CÓ SET để đảm bảo tính bất biến - không ai được sửa lịch sử giá)
    public Bidder getBidder()     { return bidder; }
    public double getBidAmount()  { return bidAmount; }
    public long   getBidTimeMs()  { return bidTimeMs; }

    /** Backward-compat: trả về LocalDateTime từ epoch ms */
    public LocalDateTime getBidTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(bidTimeMs), ZoneId.systemDefault());
    }

    @Override
    public String toString() {
        return "BidTransaction{" +
                "bidder=" + bidder.getUsername() +
                ", bidAmount=" + bidAmount +
                ", timeMs=" + bidTimeMs +
                '}';
    }
}