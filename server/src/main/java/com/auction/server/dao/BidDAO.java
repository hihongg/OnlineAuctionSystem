package com.auction.server.dao;

import com.auction.server.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BidDAO {

    /**
     * Đặt giá an toàn với Transaction + Pessimistic Locking (FOR UPDATE).
     *
     * Luồng xử lý:
     *   1. Lấy Connection riêng từ pool (không dùng chung với thread khác).
     *   2. Tắt auto-commit → bắt đầu Transaction.
     *   3. Khóa dòng item (FOR UPDATE) để tránh race condition.
     *   4. Kiểm tra nghiệp vụ (status, giá).
     *   5. Ghi bid_history + cập nhật items trong cùng 1 Transaction.
     *   6. Commit hoặc Rollback.
     *   7. Luôn đóng Connection trong finally → trả về pool.
     *
     * @return "SUCCESS" nếu thành công, "ERROR: ..." nếu thất bại.
     */
    public String placeBidTransaction(int itemId, String username, double bidAmount) {
        Connection conn = null;

        try {
            // Bước 1: Lấy connection riêng từ pool — mỗi thread có conn của mình
            conn = DatabaseConnection.getConnection();

            // Bước 2: Bắt đầu Transaction
            conn.setAutoCommit(false);

            // Bước 3: Khóa dòng sản phẩm (Pessimistic Lock)
            String checkSql = "SELECT current_highest_bid, status "
                    + "FROM items WHERE id = ? FOR UPDATE";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, itemId);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return "ERROR: Không tìm thấy sản phẩm.";
                    }

                    String status       = rs.getString("status");
                    double currentPrice = rs.getDouble("current_highest_bid");

                    // Bước 4: Kiểm tra nghiệp vụ
                    if (!"RUNNING".equals(status)) {
                        conn.rollback();
                        return "ERROR: Phiên đấu giá này đã kết thúc hoặc chưa bắt đầu.";
                    }
                    if (bidAmount <= currentPrice) {
                        conn.rollback();
                        return "ERROR: Giá đặt (" + bidAmount
                                + ") phải cao hơn giá hiện tại (" + currentPrice + ").";
                    }
                }
            }

            // Bước 5a: Lưu lịch sử đấu giá
            String insertHistorySql =
                    "INSERT INTO bid_history (item_id, username, bid_amount, bid_time) "
                            + "VALUES (?, ?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertHistorySql)) {
                insertStmt.setInt(1, itemId);
                insertStmt.setString(2, username);
                insertStmt.setDouble(3, bidAmount);
                insertStmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                insertStmt.executeUpdate();
            }

            // Bước 5b: Cập nhật giá và người dẫn đầu trên bảng items
            // Lưu ý: tên cột "highest_bidder" phải khớp với SQL schema (auction_db.sql)
            String updateItemSql =
                    "UPDATE items SET current_highest_bid = ?, highest_bidder = ? "
                            + "WHERE id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateItemSql)) {
                updateStmt.setDouble(1, bidAmount);
                updateStmt.setString(2, username);
                updateStmt.setInt(3, itemId);
                updateStmt.executeUpdate();
            }

            // Bước 6: Commit — lưu vĩnh viễn
            conn.commit();
            return "SUCCESS";

        } catch (SQLException e) {
            System.err.println("[BidDAO] Lỗi Transaction: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            return "ERROR: Lỗi hệ thống khi đặt giá.";

        } finally {
            // Bước 7: LUÔN trả connection về pool dù thành công hay thất bại
            // (Với HikariCP, conn.close() không đóng kết nối vật lý — chỉ trả về pool)
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // reset trạng thái trước khi trả pool
                    conn.close();             // ← bản cũ THIẾU dòng này → pool bị cạn
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    // =========================================================================
    // Lấy lịch sử đặt giá của 1 item — dùng cho biểu đồ giá realtime
    // =========================================================================

    /**
     * Trả về danh sách các lần đặt giá theo thứ tự thời gian tăng dần.
     * Mỗi phần tử là Map gồm: "username", "bidAmount", "bidTime" (timestamp ms).
     *
     * Client nhận JSON list này để vẽ line chart (trục X = time, trục Y = giá).
     */
    public List<Map<String, Object>> getBidHistory(int itemId) {
        List<Map<String, Object>> history = new ArrayList<>();
        String sql = "SELECT username, bid_amount, bid_time "
                + "FROM bid_history WHERE item_id = ? "
                + "ORDER BY bid_time ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, itemId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("username",  rs.getString("username"));
                    entry.put("bidAmount", rs.getDouble("bid_amount"));
                    entry.put("bidTime",   rs.getTimestamp("bid_time").getTime());
                    history.add(entry);
                }
            }

        } catch (SQLException e) {
            System.err.println("[BidDAO] getBidHistory lỗi: " + e.getMessage());
        }
        return history;
    }
}