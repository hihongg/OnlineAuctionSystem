package com.auction.client.models;

/**
 * Model phía Client đại diện cho một sản phẩm đấu giá.
 *
 * Các field được đồng bộ với shared/models/Item.java của server
 * để Gson có thể deserialize JSON trả về từ GET_ITEMS / BID_UPDATE.
 */
public class AuctionItem {

    // =========================================================================
    // FIELDS — khớp tên với Item.java (server) để Gson tự map
    // =========================================================================
    private int    id;
    private String name;
    private String description;
    private double startingPrice;
    private double currentHighestBid;      // = currentBid
    private String currentHighestBidder;   // = highestBidder
    private String status;
    private long   endTime;                // milliseconds — dùng cho đồng hồ đếm ngược
    private int    sellerId;

    // =========================================================================
    // CONSTRUCTOR (dùng khi tạo mock hoặc test thủ công)
    // =========================================================================
    public AuctionItem() {}

    public AuctionItem(int id, String name, double currentHighestBid, long endTime) {
        this.id                 = id;
        this.name               = name;
        this.currentHighestBid  = currentHighestBid;
        this.endTime            = endTime;
        this.currentHighestBidder = "Chưa có";
    }

    // =========================================================================
    // GETTERS & SETTERS
    // =========================================================================

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    /** Giá hiện tại cao nhất — tương đương currentBid */
    public double getCurrentBid() { return currentHighestBid; }
    public void setCurrentBid(double currentHighestBid) { this.currentHighestBid = currentHighestBid; }

    /** Người đang dẫn đầu */
    public String getHighestBidder() { return currentHighestBidder; }
    public void setHighestBidder(String currentHighestBidder) { this.currentHighestBidder = currentHighestBidder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /**
     * Thời gian kết thúc phiên — dạng milliseconds (epoch).
     * Dùng để tính đồng hồ đếm ngược: endTimeMs - System.currentTimeMillis()
     */
    public long getEndTimeMs() { return endTime; }
    public void setEndTimeMs(long endTime) { this.endTime = endTime; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    @Override
    public String toString() {
        return "AuctionItem{id=" + id + ", name='" + name + "', bid=" + currentHighestBid + "}";
    }
}