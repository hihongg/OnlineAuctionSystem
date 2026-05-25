package com.auction.shared.models;

import java.io.Serializable;

/**
 * Lớp trừu tượng đại diện cho một sản phẩm đấu giá.
 *
 * Áp dụng OOP:
 *   - Abstraction  : abstract class — không cho phép tạo Item "chung chung",
 *                    buộc phải chỉ rõ loại (Electronics, Art, Vehicle, ...).
 *   - Inheritance  : kế thừa Entity để nhận entityId (UUID) và createdAt tự động.
 *   - Encapsulation: mọi field private, truy cập qua getter/setter.
 *   - Polymorphism : getCategory() và printInfo() được override ở mỗi subclass,
 *                    cho phép gọi thống nhất mà không cần instanceof.
 *
 * Cây kế thừa:
 *   Entity (abstract)
 *     └─ Item (abstract)  ← file này
 *           ├─ Electronics
 *           ├─ Art
 *           └─ Vehicle
 */
public abstract class Item extends Entity implements Serializable {

    public enum Status {
        OPEN,       // Vừa tạo, chưa bắt đầu
        RUNNING,    // Đang đấu giá
        FINISHED,   // Đã kết thúc
        PAID,       // Đã thanh toán
        CANCELED    // Đã hủy
    }

    // =========================================================================
    // FIELDS (private — Encapsulation)
    // Chú ý: id ở đây là DB primary key (int), khác với entityId (UUID) của Entity.
    // =========================================================================
    private int    id;
    private String name;
    private String description;
    private double startingPrice;
    private double currentHighestBid;
    private String currentHighestBidder;
    private Status status;
    private long   startTime; // timestamp milliseconds (0 = bắt đầu ngay khi đăng)
    private long   endTime;   // timestamp milliseconds
    private int    sellerId;
    private String category;  // "ELECTRONICS" | "ART" | "VEHICLE" | "GENERAL"
    private String imagePath; // đường dẫn ảnh sản phẩm (null nếu chưa có)

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /** Constructor rỗng — cần cho Gson deserialization & Serialization */
    protected Item() {
        super(); // khởi tạo entityId + createdAt từ Entity
    }

    /** Constructor dùng trong ItemDAO.mapResultSetToItem() khi đọc từ DB */
    protected Item(int id, String name, double startingPrice) {
        super();
        this.id            = id;
        this.name          = name;
        this.startingPrice = startingPrice;
        this.currentHighestBid     = startingPrice;
        this.currentHighestBidder  = "Chưa có";
        this.status        = Status.OPEN;
    }

    /** Constructor dùng khi Seller thêm sản phẩm mới (không có startTime → bắt đầu ngay) */
    protected Item(String name, String description, double startingPrice,
                   long endTime, int sellerId) {
        super();
        this.name          = name;
        this.description   = description;
        this.startingPrice = startingPrice;
        this.currentHighestBid    = startingPrice;
        this.currentHighestBidder = "Chưa có";
        this.status        = Status.OPEN;
        this.startTime     = 0;
        this.endTime       = endTime;
        this.sellerId      = sellerId;
    }

    /** Constructor dùng khi Seller thêm sản phẩm mới với startTime đặt lịch */
    protected Item(String name, String description, double startingPrice,
                   long startTime, long endTime, int sellerId) {
        super();
        this.name          = name;
        this.description   = description;
        this.startingPrice = startingPrice;
        this.currentHighestBid    = startingPrice;
        this.currentHighestBidder = "Chưa có";
        this.status        = Status.OPEN;
        this.startTime     = startTime;
        this.endTime       = endTime;
        this.sellerId      = sellerId;
    }

    // =========================================================================
    // ABSTRACT METHODS — Polymorphism
    // Mỗi subclass buộc phải override hai phương thức này.
    // =========================================================================

    /**
     * Trả về tên danh mục của sản phẩm.
     * Ví dụ: "ELECTRONICS", "ART", "VEHICLE", "GENERAL"
     */
    public abstract String getCategory();

    /**
     * In thông tin đặc trưng của từng loại sản phẩm.
     * Thể hiện Polymorphism: cùng gọi item.printInfo() nhưng mỗi loại
     * hiển thị thông tin riêng.
     */
    public abstract String printInfo();

    // =========================================================================
    // GETTERS & SETTERS (Encapsulation)
    // =========================================================================

    public int    getId()               { return id; }
    public void   setId(int id)         { this.id = id; }

    public String getName()             { return name; }
    public void   setName(String name)  { this.name = name; }

    public String getDescription()                   { return description; }
    public void   setDescription(String description) { this.description = description; }

    public double getStartingPrice()                      { return startingPrice; }
    public void   setStartingPrice(double startingPrice)  { this.startingPrice = startingPrice; }

    public double getCurrentHighestBid()                          { return currentHighestBid; }
    public void   setCurrentHighestBid(double currentHighestBid)  { this.currentHighestBid = currentHighestBid; }

    public String getCurrentHighestBidder()                            { return currentHighestBidder; }
    public void   setCurrentHighestBidder(String currentHighestBidder) { this.currentHighestBidder = currentHighestBidder; }

    public Status getStatus()              { return status; }
    public void   setStatus(Status status) { this.status = status; }

    public long   getStartTime()               { return startTime; }
    public void   setStartTime(long startTime) { this.startTime = startTime; }

    public long   getEndTime()             { return endTime; }
    public void   setEndTime(long endTime) { this.endTime = endTime; }

    public int    getSellerId()              { return sellerId; }
    public void   setSellerId(int sellerId)  { this.sellerId = sellerId; }

    public String getCategoryField()                 { return category; }
    public void   setCategoryField(String category)  { this.category = category; }

    public String getImagePath()                     { return imagePath; }
    public void   setImagePath(String imagePath)     { this.imagePath = imagePath; }

    @Override
    public String toString() {
        return String.format("[%s] #%d %s — $%.2f (%s)",
                getCategory(), id, name, currentHighestBid, status);
    }
}