package com.auction.server.dao;

import com.auction.server.utils.DatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho BidDAO.
 *
 * Kiểm thử hàm placeBidTransaction() — hàm cốt lõi của hệ thống đấu giá.
 * Hàm này xử lý toàn bộ giao dịch trong 1 Transaction với Pessimistic Locking.
 *
 * Các trường hợp cần kiểm thử:
 *   1. Đặt giá hợp lệ (cao hơn giá hiện tại, phiên đang RUNNING) → SUCCESS
 *   2. Đặt giá thấp hơn giá hiện tại → ERROR
 *   3. Đặt giá bằng giá hiện tại → ERROR
 *   4. Phiên đấu giá đã FINISHED → ERROR
 *   5. Item ID không tồn tại → ERROR
 */
public class BidDAOTest {

    private BidDAO bidDAO;
    private int testItemId = -1;
    private String testItemName;

    // =========================================================================
    // SETUP & TEARDOWN
    // =========================================================================

    @BeforeEach
    public void setUp() throws Exception {
        bidDAO = new BidDAO();
        testItemName = "BidTestItem_" + System.currentTimeMillis();

        // Tạo item RUNNING với giá khởi điểm 500.0
        String sql = "INSERT INTO items (name, description, starting_price, current_highest_bid, "
                + "highest_bidder, status, end_time, seller_id) "
                + "VALUES (?, 'Test bid item', 500.0, 500.0, 'Chưa có', 'RUNNING', ?, 1)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, testItemName);
            // end_time là BIGINT (milliseconds) — dùng setLong, không dùng setTimestamp
            pstmt.setLong(2, System.currentTimeMillis() + 3_600_000);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    testItemId = rs.getInt(1);
                }
            }
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (testItemId > 0) {
            // CODE QUALITY FIX: Dùng PreparedStatement thay vì nối chuỗi SQL.
            // Nối chuỗi (... + testItemId) trong SQL là anti-pattern:
            //   - Dễ bị SQL Injection nếu sau này đổi thành tham số String.
            //   - Không nhất quán với phần còn lại của codebase.
            try (Connection conn = DatabaseConnection.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "DELETE FROM bid_history WHERE item_id = ?")) {
                    pstmt.setInt(1, testItemId);
                    pstmt.executeUpdate();
                }
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "DELETE FROM items WHERE id = ?")) {
                    pstmt.setInt(1, testItemId);
                    pstmt.executeUpdate();
                }
            }
        }
    }

    // =========================================================================
    // TEST 1: Đặt giá hợp lệ → SUCCESS
    // =========================================================================

    @Test
    public void testPlaceBidTransaction_giaHopLe_traveSuccess() {
        // Giá hiện tại là 500.0 → đặt 600.0 là hợp lệ
        String result = bidDAO.placeBidTransaction(testItemId, "user_A", 600.0);

        assertEquals("SUCCESS", result,
                "Đặt giá cao hơn giá hiện tại phải trả về SUCCESS");
    }

    @Test
    public void testPlaceBidTransaction_giaHopLe_capNhatDBAung() {
        bidDAO.placeBidTransaction(testItemId, "user_A", 750.0);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT current_highest_bid, highest_bidder FROM items WHERE id = ?")) {

            pstmt.setInt(1, testItemId);
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next(), "Phải tìm thấy item trong DB");
                assertEquals(750.0, rs.getDouble("current_highest_bid"), 0.001,
                        "Giá trong DB phải được cập nhật thành 750.0");
                assertEquals("user_A", rs.getString("highest_bidder"),
                        "Người dẫn đầu trong DB phải là user_A");
            }
        } catch (Exception e) {
            fail("Lỗi khi kiểm tra DB: " + e.getMessage());
        }
    }

    @Test
    public void testPlaceBidTransaction_giaHopLe_luuVaoBidHistory() {
        bidDAO.placeBidTransaction(testItemId, "user_B", 620.0);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM bid_history WHERE item_id = ? AND username = ?")) {

            pstmt.setInt(1, testItemId);
            pstmt.setString(2, "user_B");
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1),
                        "Phải có đúng 1 bản ghi lịch sử trong bid_history");
            }
        } catch (Exception e) {
            fail("Lỗi khi kiểm tra bid_history: " + e.getMessage());
        }
    }

    // =========================================================================
    // TEST 2: Giá thấp hơn giá hiện tại → ERROR
    // =========================================================================

    @Test
    public void testPlaceBidTransaction_giaThapHon_traveError() {
        String result = bidDAO.placeBidTransaction(testItemId, "user_C", 300.0);

        assertTrue(result.startsWith("ERROR"),
                "Đặt giá thấp hơn giá hiện tại phải trả về ERROR");
    }

    // =========================================================================
    // TEST 3: Giá bằng giá hiện tại → ERROR
    // =========================================================================

    @Test
    public void testPlaceBidTransaction_giaBang_traveError() {
        String result = bidDAO.placeBidTransaction(testItemId, "user_D", 500.0);

        assertTrue(result.startsWith("ERROR"),
                "Đặt giá bằng giá hiện tại phải trả về ERROR");
    }

    // =========================================================================
    // TEST 4: Phiên đã FINISHED → ERROR
    // =========================================================================

    @Test
    public void testPlaceBidTransaction_phienDaDong_traveError() throws Exception {
        // Đóng phiên đấu giá thủ công
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE items SET status = 'FINISHED' WHERE id = ?")) {
            pstmt.setInt(1, testItemId);
            pstmt.executeUpdate();
        }

        String result = bidDAO.placeBidTransaction(testItemId, "user_E", 800.0);

        assertTrue(result.startsWith("ERROR"),
                "Đặt giá vào phiên FINISHED phải trả về ERROR");
    }

    // =========================================================================
    // TEST 5: Item ID không tồn tại → ERROR
    // =========================================================================

    @Test
    public void testPlaceBidTransaction_itemKhongTonTai_traveError() {
        String result = bidDAO.placeBidTransaction(999_999, "user_F", 100.0);

        assertTrue(result.startsWith("ERROR"),
                "Đặt giá cho item không tồn tại phải trả về ERROR");
    }

    // =========================================================================
    // TEST 6: Đặt giá liên tiếp tăng dần hợp lệ
    // =========================================================================

    @Test
    public void testPlaceBidTransaction_datGiaLienTiep_tangDanHopLe() {
        String result1 = bidDAO.placeBidTransaction(testItemId, "user_G", 600.0);
        assertEquals("SUCCESS", result1, "Lần đặt giá 1 phải thành công");

        String result2 = bidDAO.placeBidTransaction(testItemId, "user_H", 700.0);
        assertEquals("SUCCESS", result2, "Lần đặt giá 2 phải thành công");

        String result3 = bidDAO.placeBidTransaction(testItemId, "user_G", 650.0);
        assertTrue(result3.startsWith("ERROR"), "Lần đặt giá 3 (thấp hơn) phải thất bại");
    }
}