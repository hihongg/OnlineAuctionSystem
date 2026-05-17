package com.auction.shared.models;

/**
 * Factory Method Pattern — tạo các loại Item phù hợp dựa trên category.
 *
 * TẠI SAO DÙNG FACTORY METHOD?
 *   Không thể gọi `new Item(...)` vì Item là abstract class.
 *   Thay vì rải `new Electronics(...)` / `new Art(...)` / `new Vehicle(...)`
 *   khắp nơi (ItemDAO, ClientHandler, ...), tập trung logic khởi tạo vào 1 chỗ.
 *
 *   Lợi ích:
 *     1. Thêm loại mới (ví dụ: RealEstate) chỉ cần sửa 1 switch-case ở đây,
 *        KHÔNG cần sửa DAO hay Handler.
 *     2. Caller không cần biết class cụ thể — chỉ cần biết category string.
 *     3. Giảm coupling giữa tầng persistence (DAO) và tầng domain (model).
 *
 * CÁCH DÙNG (trong ItemDAO.mapResultSetToItem):
 *   String category = rs.getString("category");  // đọc từ DB
 *   Item item = ItemFactory.create(category, id, name, startingPrice);
 *
 * CÁCH DÙNG (khi Seller tạo mới trong ClientHandler):
 *   Item newItem = ItemFactory.create(category, name, desc, price, endTime, sellerId);
 */
public class ItemFactory {

    /**
     * Tạo Item khi đọc từ DB (3 tham số cơ bản — các field còn lại set qua setter).
     *
     * @param category  "ELECTRONICS" | "ART" | "VEHICLE" | mặc định → "GENERAL" (Electronics)
     * @param id        DB primary key
     * @param name      Tên sản phẩm
     * @param startingPrice Giá khởi điểm
     * @return Subclass tương ứng với category
     */
    public static Item create(String category, int id, String name, double startingPrice) {
        switch (normalise(category)) {
            case "ART":
                return new Art(id, name, startingPrice);
            case "VEHICLE":
                return new Vehicle(id, name, startingPrice);
            case "ELECTRONICS":
            default:
                return new Electronics(id, name, startingPrice);
        }
    }

    /**
     * Tạo Item mới khi Seller đăng sản phẩm (5 tham số).
     *
     * @param category  "ELECTRONICS" | "ART" | "VEHICLE"
     * @param name      Tên sản phẩm
     * @param description Mô tả
     * @param startingPrice Giá khởi điểm
     * @param endTime   Thời điểm kết thúc (epoch milliseconds)
     * @param sellerId  ID người bán
     * @return Subclass tương ứng với category
     */
    public static Item create(String category,
                              String name, String description,
                              double startingPrice, long endTime, int sellerId) {
        switch (normalise(category)) {
            case "ART":
                return new Art(name, description, startingPrice, endTime, sellerId);
            case "VEHICLE":
                return new Vehicle(name, description, startingPrice, endTime, sellerId);
            case "ELECTRONICS":
            default:
                return new Electronics(name, description, startingPrice, endTime, sellerId);
        }
    }

    /**
     * Chuẩn hoá category string: trim, uppercase, null-safe.
     * "electronics" / "  ELECTRONICS  " / null → "ELECTRONICS"
     */
    private static String normalise(String category) {
        if (category == null || category.isBlank()) return "ELECTRONICS";
        return category.trim().toUpperCase();
    }

    // Constructor private — class này chỉ có static methods, không cho phép new ItemFactory()
    private ItemFactory() {}
}
