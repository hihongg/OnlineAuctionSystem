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
     * Đặt giá an toàn — Transaction + Pessimistic Locking + Kiểm tra số dư ví.
     *
     * Luồng xử lý (tất cả trong 1 Transaction):
     *   1. Lấy Connection riêng từ pool, tắt auto-commit.
     *   2. Khóa dòng item (FOR UPDATE) → đọc status, giá hiện tại, người dẫn đầu cũ.
     *   3. Kiểm tra nghiệp vụ (status RUNNING, giá đặt > giá hiện tại).
     *   4. Khóa dòng user (FOR UPDATE) → kiểm tra số dư đủ không.
     *   5. Ghi bid_history.
     *   6. Cập nhật current_highest_bid + highest_bidder trên items.
     *   7. Trừ số dư người đặt giá mới.
     *   8. Hoàn tiền cho người bị vượt qua (nếu có).
     *   9. Commit.
     *
     * @return "SUCCESS" nếu thành công, "ERROR: ..." nếu thất bại.
     */
    public String placeBidTransaction(int itemId, String username, double bidAmount) {
        Connection conn = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // ── Bước 2: Khóa dòng sản phẩm ──────────────────────────────────
            String checkSql = "SELECT current_highest_bid, highest_bidder, status "
                    + "FROM items WHERE id = ? FOR UPDATE";

            double currentPrice;
            String prevBidder;

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, itemId);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return "ERROR: Không tìm thấy sản phẩm.";
                    }

                    String status = rs.getString("status");
                    currentPrice  = rs.getDouble("current_highest_bid");
                    prevBidder    = rs.getString("highest_bidder");

                    // ── Bước 3: Kiểm tra nghiệp vụ ──────────────────────────
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

            // ── Bước 4: Khóa dòng user, kiểm tra số dư ──────────────────────
            String balanceSql = "SELECT balance FROM users WHERE username = ? FOR UPDATE";
            double balance;
            try (PreparedStatement balStmt = conn.prepareStatement(balanceSql)) {
                balStmt.setString(1, username);
                try (ResultSet brs = balStmt.executeQuery()) {
                    if (!brs.next()) {
                        conn.rollback();
                        return "ERROR: Không tìm thấy tài khoản người đặt giá.";
                    }
                    balance = brs.getDouble("balance");
                }
            }
            if (balance < bidAmount) {
                conn.rollback();
                return String.format(
                        "ERROR: Số dư không đủ. Số dư hiện tại: $%.2f, cần $%.2f. "
                                + "Vui lòng nạp thêm tiền vào ví.",
                        balance, bidAmount);
            }

            // ── Bước 5: Lưu lịch sử đặt giá ─────────────────────────────────
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

            // ── Bước 6: Cập nhật giá và người dẫn đầu ────────────────────────
            String updateItemSql =
                    "UPDATE items SET current_highest_bid = ?, highest_bidder = ? "
                            + "WHERE id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateItemSql)) {
                updateStmt.setDouble(1, bidAmount);
                updateStmt.setString(2, username);
                updateStmt.setInt(3, itemId);
                updateStmt.executeUpdate();
            }

            // ── Bước 7: Trừ số dư người đặt giá mới ─────────────────────────
            String deductSql = "UPDATE users SET balance = balance - ? WHERE username = ?";
            try (PreparedStatement deductStmt = conn.prepareStatement(deductSql)) {
                deductStmt.setDouble(1, bidAmount);
                deductStmt.setString(2, username);
                deductStmt.executeUpdate();
            }

            // ── Bước 8: Hoàn tiền cho người bị vượt qua (nếu có) ─────────────
            // "Chưa có" = chưa có ai đặt giá → không cần hoàn tiền
            if (prevBidder != null
                    && !prevBidder.equals("Chưa có")
                    && !prevBidder.equals(username)
                    && currentPrice > 0) {
                String refundSql = "UPDATE users SET balance = balance + ? WHERE username = ?";
                try (PreparedStatement refundStmt = conn.prepareStatement(refundSql)) {
                    refundStmt.setDouble(1, currentPrice);
                    refundStmt.setString(2, prevBidder);
                    refundStmt.executeUpdate();
                    System.out.printf("[BidDAO] Hoàn $%.2f cho '%s' (bị vượt giá bởi '%s')%n",
                            currentPrice, prevBidder, username);
                }
            }

            // ── Bước 9: Commit ────────────────────────────────────────────────
            conn.commit();
            return "SUCCESS";

        } catch (SQLException e) {
            System.err.println("[BidDAO] Lỗi Transaction: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            return "ERROR: Lỗi hệ thống khi đặt giá.";

        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    // =========================================================================
    // Lấy lịch sử đặt giá của 1 item — dùng cho biểu đồ giá realtime
    // =========================================================================
    public List<Map<String, Object>> getBidHistory(int itemId) {
        String sql = "SELECT username, bid_amount, bid_time "
                + "FROM bid_history WHERE item_id = ? ORDER BY bid_time ASC";
        List<Map<String, Object>> history = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, itemId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("username",   rs.getString("username"));
                    entry.put("bidAmount",  rs.getDouble("bid_amount"));
                    entry.put("bidTime",    rs.getTimestamp("bid_time").getTime());
                    history.add(entry);
                }
            }
        } catch (SQLException e) {
            System.err.println("[BidDAO] getBidHistory lỗi: " + e.getMessage());
        }
        return history;
    }
}