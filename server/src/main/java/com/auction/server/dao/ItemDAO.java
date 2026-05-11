package com.auction.server.dao;

import com.auction.shared.models.Item;
import com.auction.server.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // 1. Lấy tất cả sản phẩm đang trong trạng thái MỞ đấu giá (Hàm gốc của bạn)
    public List<Item> getActiveItems() {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE status = 'OPEN'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                items.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi tải danh sách sản phẩm đang mở: " + e.getMessage());
        }
        return items;
    }

    // 2. Lấy TOÀN BỘ sản phẩm (Kể cả đã đóng, để phục vụ việc kiểm tra gõ búa)
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
            System.err.println("Lỗi khi tải toàn bộ danh sách sản phẩm: " + e.getMessage());
        }
        return items;
    }

    // 3. Lấy chính xác 1 món đồ theo Tên (Cần thiết để Service kiểm tra giá trước khi Bid)
    public Item getItemByName(String name) {
        String sql = "SELECT * FROM items WHERE name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToItem(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi tìm sản phẩm theo tên: " + e.getMessage());
        }
        return null;
    }

    // 3b. Lấy item theo id (được AuctionService sử dụng)
    public Item getItemById(int itemId) {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, itemId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToItem(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi tìm sản phẩm theo id: " + e.getMessage());
        }
        return null;
    }

    // 4. Cập nhật lượt Đặt giá mới (Trái tim của hệ thống)
    public boolean placeBid(String itemName, double bidAmount, String username) {
        // Cập nhật giá cao nhất và người đặt giá (Theo đúng tên cột trong DB của bạn)
        String sql = "UPDATE items SET current_highest_bid = ?, highest_bidder_username = ? WHERE name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, bidAmount);
            pstmt.setString(2, username);
            pstmt.setString(3, itemName);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu lượt đấu giá: " + e.getMessage());
            return false;
        }
    }

    // 5. Cập nhật Trạng thái (Dùng để đóng phiên/gõ búa)
    public void updateStatus(String itemName, String status) {
        String sql = "UPDATE items SET status = ? WHERE name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setString(2, itemName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật trạng thái: " + e.getMessage());
        }
    }

    // 5b. Cập nhật trạng thái theo itemId
    public void updateStatus(int itemId, Item.Status status) {
        String sql = "UPDATE items SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status.name());
            pstmt.setInt(2, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi khi cập nhật trạng thái theo id: " + e.getMessage());
        }
    }

    // 6. Cập nhật Thời gian kết thúc (Phục vụ chức năng Anti-sniping +5 phút)
    public void updateEndTime(String itemName, LocalDateTime newEndTime) {
        String sql = "UPDATE items SET end_time = ? WHERE name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(newEndTime));
            pstmt.setString(2, itemName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi khi gia hạn thời gian: " + e.getMessage());
        }
    }

    // 6b. Cập nhật thời gian kết thúc theo itemId
    public void updateEndTime(int itemId, long newEndTime) {
        String sql = "UPDATE items SET end_time = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, new Timestamp(newEndTime));
            pstmt.setInt(2, itemId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi khi gia hạn thời gian theo id: " + e.getMessage());
        }
    }

    // --- HÀM PHỤ TRỢ (Helper Method) ---
    // Gom logic đọc dữ liệu từ DB thành 1 hàm để code gọn gàng, tái sử dụng cho 3 hàm Select ở trên
    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        double startingPrice = rs.getDouble("starting_price");

        Item item = new Item(id, name, startingPrice);

        // Map giá hiện tại theo logic của bạn
        double currentHighestBid = rs.getDouble("current_highest_bid");
        if (currentHighestBid > startingPrice) {
            item.setCurrentHighestBid(currentHighestBid);
            item.setCurrentHighestBidder(rs.getString("highest_bidder_username"));
        } else {
            // Nếu chưa ai đặt thì giá cao nhất tạm tính bằng giá khởi điểm
            item.setCurrentHighestBid(startingPrice);
            item.setCurrentHighestBidder("Chưa có");
        }

        // Cố gắng Map thêm Trạng thái và Thời gian kết thúc (Bắt lỗi nếu bảng DB chưa có cột này)
        try {
            String status = rs.getString("status");
            if (status != null) item.setStatus(Item.Status.valueOf(status));

            Timestamp endTime = rs.getTimestamp("end_time");
            if (endTime != null) item.setEndTime(endTime.getTime());
        } catch (SQLException ignored) {
            // Bỏ qua nếu cột status hoặc end_time không tồn tại trong MySQL
        }

        return item;
    }
}