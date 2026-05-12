package com.auction.server.dao;

import com.auction.server.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class BidDAO {

    /**
     * Hàm đặt giá an toàn với Transaction và Pessimistic Locking
     * Trả về thông báo kết quả (SUCCESS hoặc câu báo lỗi)
     */
    public String placeBidTransaction(int itemId, String username, double bidAmount) {
        Connection conn = null;

        try {
            conn = DatabaseConnection.getConnection();

            // 1. TẮT AUTO-COMMIT: Bắt đầu Transaction
            conn.setAutoCommit(false);

            // 2. KHÓA DÒNG SẢN PHẨM: Đọc giá hiện tại và khóa dòng này lại
            // (Lưu ý: Tên bảng 'items' và các cột phải khớp với CSDL của bạn)
            String checkSql = "SELECT current_highest_bid, status FROM items WHERE id = ? FOR UPDATE";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, itemId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        double currentPrice = rs.getDouble("current_highest_bid");
                        String status = rs.getString("status");

                        // Kiểm tra nghiệp vụ
                        if (!"RUNNING".equals(status)) {
                            conn.rollback();
                            return "ERROR: Phiên đấu giá này đã kết thúc hoặc chưa bắt đầu.";
                        }
                        if (bidAmount <= currentPrice) {
                            conn.rollback();
                            return "ERROR: Giá đặt (" + bidAmount + ") phải cao hơn giá hiện tại (" + currentPrice + ").";
                        }
                    } else {
                        conn.rollback();
                        return "ERROR: Không tìm thấy sản phẩm.";
                    }
                }
            }

            // 3. LƯU LỊCH SỬ VÀO bid_history (Đoạn code ban đầu của bạn)
            String insertHistorySql = "INSERT INTO bid_history (item_id, username, bid_amount, bid_time) VALUES (?, ?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertHistorySql)) {
                insertStmt.setInt(1, itemId);
                insertStmt.setString(2, username);
                insertStmt.setDouble(3, bidAmount);
                insertStmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                insertStmt.executeUpdate();
            }

            // 4. CẬP NHẬT GIÁ MỚI VÀ NGƯỜI DẪN ĐẦU VÀO BẢNG items
            String updateItemSql = "UPDATE items SET current_highest_bid = ?, highest_bidder = ? WHERE id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateItemSql)) {
                updateStmt.setDouble(1, bidAmount);
                updateStmt.setString(2, username);
                updateStmt.setInt(3, itemId);
                updateStmt.executeUpdate();
            }

            // 5. COMMIT: Mọi thứ hoàn hảo, lưu vĩnh viễn vào DB
            conn.commit();
            return "SUCCESS";

        } catch (SQLException e) {
            System.err.println("Lỗi Transaction CSDL: " + e.getMessage());
            // 6. ROLLBACK: Nếu rớt mạng hoặc lỗi SQL giữa chừng, hoàn tác toàn bộ
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            return "ERROR: Lỗi hệ thống khi đặt giá.";
        } finally {
            // 7. TRẢ LẠI TRẠNG THÁI GỐC CHO CONNECTION
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}