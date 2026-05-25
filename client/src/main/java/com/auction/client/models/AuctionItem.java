package com.auction.client.models;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * DTO phía Client — ánh xạ 1-1 với JSON của Item trả về từ server.
 *
 * TẠI SAO KHÔNG DÙNG Item từ shared trực tiếp?
 *   Item là abstract class, không thể new. Gson không thể deserialize abstract class.
 *   AuctionItem dùng field names GIỐNG HỆT Item.java để Gson tự map khi parse JSON.
 *
 * FIX: imagePath KHÔNG được đánh dấu transient nữa.
 *   Trước đây field này là "transient" nên Gson bỏ qua khi deserialize JSON từ server
 *   → dù server gửi imagePath về, client vẫn không nhận được → card/detail không có ảnh.
 */
public class AuctionItem {

    // ─── Fields khớp tên với Item.java (để Gson tự deserialize từ server JSON) ─
    private int    id;
    private String name;
    private String description;
    private double startingPrice;
    private double currentHighestBid;
    private String currentHighestBidder;
    private String status;      // "OPEN" | "RUNNING" | "FINISHED" | "PAID" | "CANCELED"
    private long   endTime;     // epoch milliseconds (khớp Item.endTime)
    private int    sellerId;
    private String category;    // "ELECTRONICS" | "ART" | "VEHICLE"

    // ─── FIX: BỎ từ khóa "transient" để Gson deserialize được từ JSON server ──
    // imagePath được server trả về trong JSON (sau khi upload ảnh).
    // Nếu để "transient", Gson bỏ qua field này hoàn toàn → ảnh không bao giờ hiện.
    private String imagePath;

    // -------------------------------------------------------------------------
    // Constructor dùng cho mock/test data (giữ backward compat)
    // -------------------------------------------------------------------------
    public AuctionItem(String name, double currentHighestBid, LocalDateTime endTimeLocal,
                       String imagePath, String description) {
        this.name               = name;
        this.currentHighestBid  = currentHighestBid;
        this.endTime = endTimeLocal.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        this.imagePath          = imagePath;
        this.description        = description;
        this.currentHighestBidder = "Chưa có";
        this.status             = "RUNNING";
    }

    // -------------------------------------------------------------------------
    // GETTERS
    // -------------------------------------------------------------------------
    public int    getId()                     { return id; }
    public String getName()                   { return name; }
    public String getDescription()            { return description; }
    public double getStartingPrice()          { return startingPrice; }
    public String getCurrentHighestBidder()   { return currentHighestBidder; }
    public String getStatus()                 { return status; }
    public long   getEndTimeEpoch()           { return endTime; }
    public int    getSellerId()               { return sellerId; }
    public String getCategory()               { return category; }
    public String getImagePath()              { return imagePath; }

    /** Giá hiện tại cao nhất */
    public double getCurrentBid()             { return currentHighestBid; }
    public double getCurrentHighestBid()      { return currentHighestBid; }

    /**
     * Chuyển endTime (epoch ms) → LocalDateTime để ItemDetailController dùng đếm ngược.
     * Nếu endTime chưa set (= 0) trả về 1 giờ từ bây giờ để tránh crash.
     */
    public LocalDateTime getEndTime() {
        if (endTime <= 0) return LocalDateTime.now().plusHours(1);
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault());
    }

    /** Format endTime thành chuỗi dễ đọc cho hiển thị */
    public String getEndTimeFormatted() {
        if (endTime <= 0) return "N/A";
        return getEndTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    public boolean isRunning()  { return "RUNNING".equals(status); }
    public boolean isFinished() { return "FINISHED".equals(status) || "PAID".equals(status) || "CANCELED".equals(status); }

    // -------------------------------------------------------------------------
    // SETTERS — dùng khi nhận BID_UPDATE từ server
    // -------------------------------------------------------------------------
    public void setCurrentBid(double bid)                   { this.currentHighestBid = bid; }
    public void setCurrentHighestBid(double bid)            { this.currentHighestBid = bid; }
    public void setCurrentHighestBidder(String bidder)      { this.currentHighestBidder = bidder; }
    public void setEndTime(long endTime)                    { this.endTime = endTime; }
    public void setStatus(String status)                    { this.status = status; }
    public void setImagePath(String imagePath)              { this.imagePath = imagePath; }

    @Override
    public String toString() {
        return String.format("[%s] #%d %s — $%.2f (%s)", category, id, name, currentHighestBid, status);
    }
}