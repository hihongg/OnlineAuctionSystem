package com.auction.shared.models;

/**
 * Sản phẩm đấu giá thuộc danh mục Điện tử (Electronics).
 *
 * Kế thừa Item và override các phương thức trừu tượng.
 * Thể hiện Polymorphism: gọi item.printInfo() sẽ tự động gọi
 * phiên bản của Electronics mà không cần kiểm tra kiểu.
 */
public class Electronics extends Item {

    // Thông số đặc trưng của thiết bị điện tử
    private String brand;       // Hãng sản xuất (Dell, Apple, Samsung...)
    private String warrantyInfo; // Thông tin bảo hành

    // Constructor rỗng cho Gson
    public Electronics() {
        super();
    }

    // Constructor dùng khi đọc từ DB
    public Electronics(int id, String name, double startingPrice) {
        super(id, name, startingPrice);
    }

    // Constructor dùng khi Seller tạo mới (bắt đầu ngay)
    public Electronics(String name, String description, double startingPrice,
                       long endTime, int sellerId) {
        super(name, description, startingPrice, endTime, sellerId);
    }

    // Constructor dùng khi Seller đặt lịch đấu giá (có startTime)
    public Electronics(String name, String description, double startingPrice,
                       long startTime, long endTime, int sellerId) {
        super(name, description, startingPrice, startTime, endTime, sellerId);
    }

    // Thêm thông tin bảo hành — đặc trưng Electronics
    public Electronics(String name, String description, double startingPrice,
                       long endTime, int sellerId,
                       String brand, String warrantyInfo) {
        super(name, description, startingPrice, endTime, sellerId);
        this.brand        = brand;
        this.warrantyInfo = warrantyInfo;
    }

    @Override
    public String getCategory() {
        return "ELECTRONICS";
    }

    @Override
    public String printInfo() {
        return String.format(
                "=== ĐIỆN TỬ ===\n  Tên       : %s\n  Hãng      : %s\n  Bảo hành  : %s\n  Giá hiện  : $%.2f\n  Trạng thái: %s",
                getName(),
                brand != null ? brand : "N/A",
                warrantyInfo != null ? warrantyInfo : "N/A",
                getCurrentHighestBid(),
                getStatus()
        );
    }

    public String getBrand()             { return brand; }
    public void   setBrand(String brand) { this.brand = brand; }

    public String getWarrantyInfo()                    { return warrantyInfo; }
    public void   setWarrantyInfo(String warrantyInfo) { this.warrantyInfo = warrantyInfo; }
}