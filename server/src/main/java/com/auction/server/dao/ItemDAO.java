package com.auction.server.dao;

import com.auction.shared.models.Item;
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
    //
    // BUG CŨ: WHERE status = 'OPEN'
    //   → Dữ liệu mẫu trong SQL là RUNNING, nên client luôn nhận danh sách rỗng.
    //
    // FIX: WHERE status IN ('OPEN', 'RUNNING')
    //   - OPEN   = phiên vừa tạo, chưa đến giờ bắt đầu (Seller vừa đăng).
    //   - RUNNING = đang diễn ra, Bidder có thể đặt giá.
    //   Cả hai trạng thái đều nên hiển thị trong danh sách cho Bidder.
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
    // 2. Lấy TOÀN BỘ sản phẩm (Scheduler dùng để kiểm tra OPEN→RUNNING và gõ búa)
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
    // 3b. Lấy item theo tên (giữ lại cho tương thích với code cũ nếu cần)
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
    // 4a. Cập nhật bid theo itemId (ĐÚNG — dùng WHERE id = ?)
    //
    // BUG CŨ: placeBid(String itemName, ...) dùng WHERE name = ?
    //   → AuctionService gọi placeBid(String.valueOf(itemId), ...) truyền "123"
    //     vào WHERE name = ? → không tìm được row nào → update 0 dòng → luôn false.
    //
    // FIX: Thêm placeBidById(int itemId, ...) dùng WHERE id = ?
    //   AuctionService và BidDAO đều dùng hàm này.
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
    // 4b. Giữ lại hàm cũ theo tên (tương thích với các test hoặc code khác)
    //     Chú ý: hàm này chỉ dùng khi thực sự biết tên item.
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
    // 5a. Cập nhật trạng thái theo itemId (dùng trong Scheduler và BidDAO)
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
    // 6. Cập nhật thời gian kết thúc theo itemId (Anti-sniping +5 phút)
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
    // 7. Kích hoạt các phiên OPEN đã đến giờ start_time → chuyển thành RUNNING
    //
    // FIX MỚI (Bug 5): AuctionService.refreshAuctionsStatus() trước đây chỉ xử lý
    //   RUNNING → FINISHED, không bao giờ chuyển OPEN → RUNNING.
    //   Kết quả: Seller đăng sản phẩm mới (status=OPEN) nhưng mãi không được đấu giá.
    //
    // Hàm này được Scheduler gọi mỗi giây, trả về số phiên vừa được kích hoạt.
    // =========================================================================
    public int activatePendingItems() {
        // start_time là TIMESTAMP — phiên nào đã qua giờ bắt đầu thì kích hoạt
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
    //    Trả về id tự sinh của dòng vừa INSERT, hoặc -1 nếu thất bại.
    // =========================================================================
    public int addItem(Item item) {
        String sql = "INSERT INTO items (name, description, starting_price, current_highest_bid, "
                + "highest_bidder, status, end_time, seller_id) "
                + "VALUES (?, ?, ?, ?, 'Chưa có', 'OPEN', ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql,
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, item.getName());
            pstmt.setString(2, item.getDescription());
            pstmt.setDouble(3, item.getStartingPrice());
            pstmt.setDouble(4, item.getStartingPrice()); // current = starting
            pstmt.setLong(5, item.getEndTime());
            pstmt.setInt(6, item.getSellerId());

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
    // 9. Cập nhật thông tin sản phẩm (Seller chỉ sửa được phiên chưa RUNNING)
    //    Trả về true nếu update thành công.
    // =========================================================================
    public boolean updateItem(int itemId, String name, String description,
                              double startingPrice, long endTime) {
        // Chỉ cho phép sửa khi phiên còn OPEN (chưa bắt đầu đấu giá)
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
    // 10. Xóa sản phẩm (Admin hoặc Seller xóa phiên chưa RUNNING)
    //     Cascade sẽ tự xóa bid_history liên quan (nhờ ON DELETE CASCADE).
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
    // 11. Lấy danh sách sản phẩm theo Seller (Seller quản lý sản phẩm của mình)
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
    //
    // BUG CŨ 1 (đã sửa): rs.getTimestamp("end_time") trên cột BIGINT
    //   → JDBC đọc BIGINT dưới dạng giây (Unix epoch), không phải milliseconds.
    //   → Ví dụ: end_time lưu 1_716_000_000_000 ms, getTimestamp() trả Timestamp
    //     tương ứng với năm ~56000 → hoàn toàn sai.
    //   FIX: rs.getLong("end_time") — lấy thẳng số milliseconds đúng như DB lưu.
    //
    // BUG CŨ 2 (đã sửa): if (currentHighestBid > startingPrice)
    //   → Khi bid đầu tiên đúng bằng startingPrice, điều kiện false
    //     → item.setCurrentHighestBidder("Chưa có") dù thực tế đã có người đặt giá.
    //   FIX: Luôn lấy giá từ DB; chỉ fallback về startingPrice khi DB trả về 0.
    // =========================================================================
    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        int id            = rs.getInt("id");
        String name       = rs.getString("name");
        double startPrice = rs.getDouble("starting_price");

        Item item = new Item(id, name, startPrice);

        // --- Giá hiện tại ---
        // FIX Bug cũ 2: dùng > 0 thay vì > startingPrice
        double currentBid = rs.getDouble("current_highest_bid");
        if (currentBid > 0) {
            item.setCurrentHighestBid(currentBid);
            String bidder = rs.getString("highest_bidder");
            item.setCurrentHighestBidder(bidder != null && !bidder.isBlank() ? bidder : "Chưa có");
        }
        // else: constructor đã gán currentHighestBid = startingPrice, "Chưa có"

        // --- Description ---
        item.setDescription(rs.getString("description"));

        // --- Status ---
        String statusStr = rs.getString("status");
        if (statusStr != null) {
            try {
                item.setStatus(Item.Status.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                System.err.println("[ItemDAO] Status không hợp lệ: " + statusStr);
            }
        }

        // --- End time: FIX Bug cũ 1 — cột BIGINT lưu milliseconds, dùng getLong ---
        long endTime = rs.getLong("end_time");
        item.setEndTime(endTime);   // 0 = không có hạn chót

        // --- Seller ---
        item.setSellerId(rs.getInt("seller_id"));

        return item;
    }
}