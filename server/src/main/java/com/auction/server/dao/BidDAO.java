package com.auction.server.dao;

import com.auction.server.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class BidDAO {

    // Lưu trữ giao dịch đặt giá thành công vào CSDL
    public boolean saveBidHistory(int itemId, String username, double bidAmount) {
        String sql = "INSERT INTO bid_history (item_id, username, bid_amount, bid_time) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, itemId);
            pstmt.setString(2, username);
            pstmt.setDouble(3, bidAmount);
            pstmt.setTimestamp(4, new Timestamp(System.currentTimeMillis())); // Lấy thời gian thực

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi lưu lịch sử đấu giá: " + e.getMessage());
            return false;
        }
    }
}