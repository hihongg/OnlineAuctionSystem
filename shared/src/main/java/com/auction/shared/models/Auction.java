package com.auction.shared.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đại diện cho một phiên đấu giá.
 *
 * THAY ĐỔI: Dùng Item.Status thay vì AuctionStatus để thống nhất trạng thái
 * trong toàn hệ thống (trước đây có 2 enum song song gây nhầm lẫn).
 *
 * Trạng thái hợp lệ theo luồng nghiệp vụ:
 *   OPEN → RUNNING → FINISHED → PAID / CANCELED
 */
public class Auction extends Entity {

    private Item item;               // Sản phẩm được đấu giá
    private Seller seller;           // Người tạo phiên đấu giá

    // FIX: transient — Gson bỏ qua, cùng lý do với Entity.createdAt.
    // startTime/endTime của Auction không cần truyền qua socket (dùng Item.endTime thay thế).
    private transient LocalDateTime startTime;
    private transient LocalDateTime endTime;

    // FIX: Dùng Item.Status (OPEN, RUNNING, FINISHED, PAID, CANCELED)
    // thay vì AuctionStatus riêng biệt — thống nhất enum trong toàn project.
    private Item.Status status;

    private BidTransaction currentHighestBid;
    private List<BidTransaction> bidHistory;

    public Auction(Item item, Seller seller, LocalDateTime startTime, LocalDateTime endTime) {
        super();
        this.item      = item;
        this.seller    = seller;
        this.startTime = startTime;
        this.endTime   = endTime;
        this.status    = Item.Status.OPEN; // Mặc định khi mới tạo
        this.bidHistory = new ArrayList<>();
        this.currentHighestBid = null;
    }

    /**
     * Thêm một lượt đặt giá mới vào phiên — kiểm soát tập trung qua method này
     * thay vì trả List ra ngoài để add trực tiếp (Encapsulation).
     */
    public void addBid(BidTransaction newBid) {
        this.bidHistory.add(newBid);
        this.currentHighestBid = newBid;
    }

    // ── Getters & Setters ──────────────────────────────────────────────

    public Item            getItem()       { return item; }
    public Seller          getSeller()     { return seller; }
    public LocalDateTime   getStartTime()  { return startTime; }
    public LocalDateTime   getEndTime()    { return endTime; }
    public void            setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Item.Status     getStatus()     { return status; }
    public void            setStatus(Item.Status status) { this.status = status; }

    public BidTransaction  getCurrentHighestBid() { return currentHighestBid; }

    /** Trả về bản sao để tránh bên ngoài thay đổi trực tiếp (Defensive copying). */
    public List<BidTransaction> getBidHistory() {
        return new ArrayList<>(bidHistory);
    }
}