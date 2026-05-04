package com.auction.server.dao;

import com.auction.shared.models.Item;
import com.auction.server.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp; // Bổ sung thư viện cho thời gian
import java.time.LocalDateTime; // Bổ sung thư viện cho thời gian
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // 1. Chức năng Thêm Sản phẩm (Giữ nguyên của bạn)
    public boolean addItem(Item item) {
        Connection conn = DatabaseConnection.getInstance().getConnection();
        String sql = "INSERT INTO items (name, starting_price, current_price, top_bidder) VALUES (?, ?, ?, ?)";
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, item.getName());
            ps.setDouble(2, item.getStartingPrice());
            ps.setDouble(3, item.getCurrentPrice());
            ps.setString(4, item.getTopBidder());

            int result = ps.executeUpdate();
            return result > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi thêm sản phẩm: " + e.getMessage());
            return false;
        }
    }

    // 2. Lấy TOÀN BỘ danh sách (Giữ nguyên của bạn)
    public List<Item> getAllItems() {
        List<Item> itemList = new ArrayList<>();
        Connection conn = DatabaseConnection.getInstance().getConnection();
        String sql = "SELECT * FROM items";

        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                itemList.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách SP: " + e.getMessage());
        }
        return itemList;
    }

    // ---------------------------------------------------------
    // CÁC HÀM MỚI ĐƯỢC BỔ SUNG ĐỂ PHỤC VỤ CHO AUCTION SERVICE
    // ---------------------------------------------------------

    // 3. Lấy danh sách CÁC MÓN ĐANG MỞ BÁN (Phục vụ hàm getActiveAuctions)
    public List<Item> getAllOpenItems() {
        List<Item> itemList = new ArrayList<>();
        Connection conn = DatabaseConnection.getInstance().getConnection();
        String sql = "SELECT * FROM items WHERE status = 'OPEN'";

        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                itemList.add(mapResultSetToItem(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách SP đang mở: " + e.getMessage());
        }
        return itemList;
    }

    // 4. Tìm kiếm chính xác 1 món đồ bằng Tên (Phục vụ việc check hợp lệ trước khi Bid)
    public Item getItemByName(String name) {
        Connection conn = DatabaseConnection.getInstance().getConnection();
        String sql = "SELECT * FROM items WHERE name = ?";
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return mapResultSetToItem(rs);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm kiếm sản phẩm: " + e.getMessage());
        }
        return null; // Không tìm thấy
    }

    // 5. Cập nhật giá (Trái tim đấu giá) - Đổi tên từ updatePrice thành placeBid cho khớp với Service
    public boolean placeBid(String itemName, double newPrice, String user) {
        Connection conn = DatabaseConnection.getInstance().getConnection();
        String sql = "UPDATE items SET current_price = ?, top_bidder = ? WHERE name = ? AND current_price < ?";
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setDouble(1, newPrice);
            ps.setString(2, user);
            ps.setString(3, itemName);
            ps.setDouble(4, newPrice);

            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật giá: " + e.getMessage());
            return false;
        }
    }

    // 6. Cập nhật trạng thái (Dùng để GÕ BÚA - Đóng/Mở phiên)
    public void updateStatus(String itemName, String status) {
        Connection conn = DatabaseConnection.getInstance().getConnection();
        String sql = "UPDATE items SET status = ? WHERE name = ?";
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, status);
            ps.setString(2, itemName);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái: " + e.getMessage());
        }
    }

    // 7. Cập nhật thời gian kết thúc (Phục vụ tính năng ANTI-SNIPING +5 phút của bạn)
    public void updateEndTime(String itemName, LocalDateTime newEndTime) {
        Connection conn = DatabaseConnection.getInstance().getConnection();
        String sql = "UPDATE items SET end_time = ? WHERE name = ?";
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            // Convert LocalDateTime của Java sang Timestamp của MySQL
            ps.setTimestamp(1, Timestamp.valueOf(newEndTime));
            ps.setString(2, itemName);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật thời gian kết thúc: " + e.getMessage());
        }
    }

    // --- HÀM PHỤ TRỢ (Helper Method) ---
    // Gom code mapping Database sang Object Item để tái sử dụng, tránh viết lại nhiều lần
    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        Item item = new Item();
        item.setId(rs.getInt("id"));
        item.setName(rs.getString("name"));
        item.setStartingPrice(rs.getDouble("starting_price"));
        item.setCurrentPrice(rs.getDouble("current_price"));
        item.setTopBidder(rs.getString("top_bidder"));

        // Cố gắng lấy thêm trạng thái và thời gian (NẾU trong Class Item của bạn đã có 2 thuộc tính này)
        // Nếu file Item.java của bạn chưa có status và endTime, hãy thêm vào nhé!
        try {
            item.setStatus(rs.getString("status"));
            Timestamp endTimeTS = rs.getTimestamp("end_time");
            if (endTimeTS != null) {
                item.setEndTime(endTimeTS.toLocalDateTime());
            }
        } catch (SQLException ignored) {
            // Bỏ qua nếu bảng items chưa có cột status và end_time
        }
        return item;
    }
}