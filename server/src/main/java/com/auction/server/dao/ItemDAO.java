package com.auction.server.dao;

import com.auction.shared.models.Item;
import com.auction.shared.models.ItemFactory;
import com.auction.server.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // =========================================================================
    // 1. Lấy sản phẩm hiển thị cho Bidder (OPEN hoặc RUNNING)
    // =========================================================================
    public List<Item> getActiveItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE status IN ('OPEN', 'RUNNING') ORDER BY end_time ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] getActiveItems lỗi: " + e.getMessage());
        }
        return items;
    }

    // =========================================================================
    // 2. Lấy TOÀN BỘ sản phẩm (Admin dùng, hoặc các truy vấn cần toàn bộ)
    // =========================================================================
    public List<Item> getAllItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] getAllItems lỗi: " + e.getMessage());
        }
        return items;
    }

    // =========================================================================
    // 2b. PERFORMANCE FIX: Lấy chỉ các phiên RUNNING đã hết giờ (Scheduler gõ búa)
    //
    // Vấn đề cũ: refreshAuctionsStatus() gọi getAllItems() mỗi giây → lấy toàn
    //   bộ bảng items kể cả OPEN/FINISHED/CANCELED không cần thiết.
    //
    // Fix: Thêm hàm này chỉ trả về RUNNING items có end_time đã qua.
    //   Giảm số dòng DB đọc từ N (tất cả) xuống còn k (phiên sắp đóng, thường = 0).
    // =========================================================================
    public List<Item> getRunningItemsToClose(long nowMs) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE status = 'RUNNING' AND end_time > 0 AND end_time <= ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, nowMs);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    items.add(mapResultSetToItem(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] getRunningItemsToClose lỗi: " + e.getMessage());
        }
        return items;
    }

    // =========================================================================
    // 3a. Lấy item theo ID
    // =========================================================================
    public Item getItemById(int itemId) {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, itemId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapResultSetToItem(rs);
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] getItemById lỗi: " + e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // 3b. Lấy item theo tên
    // =========================================================================
    public Item getItemByName(String name) {
        String sql = "SELECT * FROM items WHERE name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapResultSetToItem(rs);
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] getItemByName lỗi: " + e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // 4a. Cập nhật bid theo itemId (dùng WHERE id = ?)
    // =========================================================================
    public boolean placeBidById(int itemId, double bidAmount, String username) {
        String sql = "UPDATE items SET current_highest_bid = ?, highest_bidder = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, bidAmount);
            pstmt.setString(2, username);
            pstmt.setInt(3, itemId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] placeBidById lỗi: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 4b. Cập nhật bid theo tên (tương thích ngược)
    // =========================================================================
    public boolean placeBid(String itemName, double bidAmount, String username) {
        String sql = "UPDATE items SET current_highest_bid = ?, highest_bidder = ? WHERE name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, bidAmount);
            pstmt.setString(2, username);
            pstmt.setString(3, itemName);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] placeBid lỗi: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 5a. Cập nhật trạng thái theo itemId
    // =========================================================================
    public void updateStatus(int itemId, Item.Status status) {
        String sql = "UPDATE items SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status.name());
            pstmt.setInt(2, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ItemDAO] updateStatus(id) lỗi: " + e.getMessage());
        }
    }

    // =========================================================================
    // 5b. Cập nhật trạng thái theo tên (tương thích ngược)
    // =========================================================================
    public void updateStatus(String itemName, String status) {
        String sql = "UPDATE items SET status = ? WHERE name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setString(2, itemName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ItemDAO] updateStatus(name) lỗi: " + e.getMessage());
        }
    }

    // =========================================================================
    // 6. Cập nhật thời gian kết thúc (Anti-sniping +5 phút)
    // =========================================================================
    public void updateEndTime(int itemId, long newEndTimeMs) {
        String sql = "UPDATE items SET end_time = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, newEndTimeMs);
            pstmt.setInt(2, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ItemDAO] updateEndTime lỗi: " + e.getMessage());
        }
    }

    // =========================================================================
    // 7. Kích hoạt các phiên OPEN đã đến giờ → RUNNING
    // =========================================================================
    public int activatePendingItems() {
        String sql = "UPDATE items SET status = 'RUNNING' "
                + "WHERE status = 'OPEN' AND start_time <= NOW()";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int count = pstmt.executeUpdate();
            if (count > 0) {
                System.out.println("[ItemDAO] Kích hoạt " + count + " phiên OPEN → RUNNING.");
            }
            return count;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] activatePendingItems lỗi: " + e.getMessage());
            return 0;
        }
    }

    // =========================================================================
    // 8. Thêm sản phẩm mới (Seller dùng)
    // =========================================================================
    public int addItem(Item item) {
        String sql = "INSERT INTO items (name, description, starting_price, current_highest_bid, "
                + "highest_bidder, status, end_time, seller_id, category) "
                + "VALUES (?, ?, ?, ?, 'Chưa có', 'OPEN', ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql,
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, item.getName());
            pstmt.setString(2, item.getDescription());
            pstmt.setDouble(3, item.getStartingPrice());
            pstmt.setDouble(4, item.getStartingPrice());
            pstmt.setLong(5, item.getEndTime());
            pstmt.setInt(6, item.getSellerId());
            pstmt.setString(7, item.getCategory()); // Factory Method: lưu loại item vào DB

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] addItem lỗi: " + e.getMessage());
        }
        return -1;
    }

    // =========================================================================
    // 9. Cập nhật thông tin sản phẩm (chỉ khi còn OPEN)
    // =========================================================================
    public boolean updateItem(int itemId, String name, String description,
                              double startingPrice, long endTime) {
        String sql = "UPDATE items SET name=?, description=?, starting_price=?, end_time=? "
                + "WHERE id=? AND status='OPEN'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.setDouble(3, startingPrice);
            pstmt.setLong(4, endTime);
            pstmt.setInt(5, itemId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] updateItem lỗi: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 10. Xóa sản phẩm (chỉ khi OPEN/FINISHED/CANCELED)
    // =========================================================================
    public boolean deleteItem(int itemId) {
        String sql = "DELETE FROM items WHERE id=? AND status IN ('OPEN', 'FINISHED', 'CANCELED')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, itemId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ItemDAO] deleteItem lỗi: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 11. Lấy danh sách sản phẩm theo Seller
    // =========================================================================
    public List<Item> getItemsBySeller(int sellerId) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE seller_id = ? ORDER BY id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, sellerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("[ItemDAO] getItemsBySeller lỗi: " + e.getMessage());
        }
        return items;
    }

    // =========================================================================
    // HELPER — đọc một dòng ResultSet thành object Item
    // =========================================================================
    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        int    id         = rs.getInt("id");
        String name       = rs.getString("name");
        double startPrice = rs.getDouble("starting_price");

        // Factory Method: đọc category từ DB, tạo đúng subclass
        // (Electronics / Art / Vehicle). Item cũ chưa có cột category → mặc định ELECTRONICS.
        String category = rs.getString("category");
        Item item = ItemFactory.create(category, id, name, startPrice);

        double currentBid = rs.getDouble("current_highest_bid");
        if (currentBid > 0) {
            item.setCurrentHighestBid(currentBid);
            String bidder = rs.getString("highest_bidder");
            item.setCurrentHighestBidder(bidder != null && !bidder.isBlank() ? bidder : "Chưa có");
        }

        item.setDescription(rs.getString("description"));

        String statusStr = rs.getString("status");
        if (statusStr != null) {
            try {
                item.setStatus(Item.Status.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                System.err.println("[ItemDAO] Status không hợp lệ: " + statusStr);
            }
        }

        long endTime = rs.getLong("end_time");
        item.setEndTime(endTime);

        item.setSellerId(rs.getInt("seller_id"));

        return item;
    }
}