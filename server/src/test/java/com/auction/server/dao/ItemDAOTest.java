package com.auction.server.dao;

import com.auction.server.utils.DatabaseConnection;
import com.auction.shared.models.Item;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Unit Test cho ItemDAO.
 *
 * Mỗi test tự tạo dữ liệu mẫu (@BeforeEach) và tự dọn dẹp (@AfterEach)
 * để không ảnh hưởng dữ liệu thật trong DB.
 */
public class ItemDAOTest {

    private ItemDAO itemDAO;
    private int testItemId = -1;
    private String testItemName;

    @BeforeEach
    public void setUp() throws SQLException {
        itemDAO = new ItemDAO();
        testItemName = "TestItem_" + System.currentTimeMillis();

        // Tạo item RUNNING để test có thể tìm thấy qua getActiveItems()
        // (FIX Bug 1: getActiveItems() giờ trả cả OPEN lẫn RUNNING)
        String sql = "INSERT INTO items (name, description, starting_price, current_highest_bid, "
                + "highest_bidder, status, end_time, seller_id) "
                + "VALUES (?, 'Mô tả test', 100.0, 100.0, 'Chưa có', 'RUNNING', "
                + "(UNIX_TIMESTAMP() + 3600) * 1000, 1)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, testItemName);
            pstmt.executeUpdate();

            var keys = pstmt.getGeneratedKeys();
            if (keys.next()) {
                testItemId = keys.getInt(1);
            }
        }
    }

    @AfterEach
    public void tearDown() throws SQLException {
        if (testItemId == -1) return;

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement p1 = conn.prepareStatement(
                    "DELETE FROM bid_history WHERE item_id = ?")) {
                p1.setInt(1, testItemId);
                p1.executeUpdate();
            }
            try (PreparedStatement p2 = conn.prepareStatement(
                    "DELETE FROM items WHERE id = ?")) {
                p2.setInt(1, testItemId);
                p2.executeUpdate();
            }
        }
    }

    // =========================================================================
    // TEST 1: getActiveItems() — phải tìm thấy item RUNNING vừa tạo
    //
    // FIX Bug 1: Trước đây query WHERE status='OPEN' không bao giờ trả về
    // item RUNNING → test này luôn fail.
    // =========================================================================
    @Test
    public void testGetActiveItems_findRunningItem() {
        List<Item> items = itemDAO.getActiveItems();

        assertNotNull(items, "getActiveItems() không được trả về null");

        boolean found = items.stream().anyMatch(i -> i.getName().equals(testItemName));
        assertTrue(found, "Phải tìm thấy item RUNNING '" + testItemName + "' trong danh sách active");
    }

    @Test
    public void testGetActiveItems_notReturnFinished() throws SQLException {
        // Đổi item test thành CANCELED
        // Lưu ý: getActiveItems() hiện trả về TẤT CẢ trạng thái TRỪ CANCELED
        // (bao gồm RUNNING, OPEN, FINISHED, PAID) — UX giống eBay, người dùng
        // vẫn thấy phiên đã kết thúc trên dashboard.
        // Do đó test này kiểm tra CANCELED không xuất hiện (thay vì FINISHED).
        itemDAO.updateStatus(testItemId, Item.Status.CANCELED);

        List<Item> items = itemDAO.getActiveItems();

        boolean found = items.stream().anyMatch(i -> i.getName().equals(testItemName));
        assertFalse(found, "Item CANCELED không được xuất hiện trong getActiveItems()");
    }

    // =========================================================================
    // TEST 2: getItemById() — kiểm tra mapping đầy đủ
    //
    // FIX Bug 2: end_time từ DB (BIGINT milliseconds) giờ được đọc đúng bằng getLong().
    // FIX Bug 3: currentHighestBid được map đúng dù bằng startingPrice.
    // =========================================================================
    @Test
    public void testGetItemById_correctMapping() {
        Item item = itemDAO.getItemById(testItemId);

        assertNotNull(item, "Phải tìm thấy item khi ID tồn tại");
        assertEquals(testItemName, item.getName(), "Tên phải khớp");
        assertEquals(100.0, item.getStartingPrice(), 0.01, "Giá khởi điểm phải là 100.0");
        assertEquals(Item.Status.RUNNING, item.getStatus(), "Trạng thái phải là RUNNING");

        // FIX Bug 2: end_time phải > 0 (đọc đúng từ BIGINT milliseconds)
        assertTrue(item.getEndTime() > 0,
                "end_time phải > 0 (FIX: dùng getLong thay vì getTimestamp cho cột BIGINT)");

        // FIX Bug 2: kiểm tra end_time hợp lý (khoảng 1 giờ trong tương lai ± sai số nhỏ)
        long now = System.currentTimeMillis();
        assertTrue(item.getEndTime() > now,
                "end_time phải ở trong tương lai");
        assertTrue(item.getEndTime() < now + 2 * 3600 * 1000L,
                "end_time không được quá 2 giờ tính từ bây giờ");
    }

    @Test
    public void testGetItemById_notFound() {
        Item item = itemDAO.getItemById(-999);
        assertNull(item, "Phải trả về null khi ID không tồn tại");
    }

    // =========================================================================
    // TEST 3: getItemByName()
    // =========================================================================
    @Test
    public void testGetItemByName_existingItem() {
        Item item = itemDAO.getItemByName(testItemName);

        assertNotNull(item, "Phải tìm thấy item khi tên tồn tại");
        assertEquals(testItemId, item.getId(), "ID phải khớp");
    }

    @Test
    public void testGetItemByName_notFound() {
        Item item = itemDAO.getItemByName("TênKhôngTồnTại_xyz_999");
        assertNull(item, "Phải trả về null khi tên không tồn tại");
    }

    // =========================================================================
    // TEST 4: placeBidById() — kiểm tra cập nhật giá theo ID
    //
    // BUG CŨ: test gọi itemDAO.placeBid(String.valueOf(testItemId), ...)
    //   → WHERE name = '123' → 0 rows updated → luôn false.
    //
    // FIX: Dùng placeBidById(int id, ...) — WHERE id = ? → đúng.
    // =========================================================================
    @Test
    public void testPlaceBidById_updatesPrice() {
        double newBid  = 250.0;
        String bidder  = "bidder_test";

        // FIX Bug 4: Gọi placeBidById(int, ...) thay vì placeBid(String, ...)
        boolean result = itemDAO.placeBidById(testItemId, newBid, bidder);

        assertTrue(result, "placeBidById() phải trả về true khi cập nhật thành công");

        Item updated = itemDAO.getItemById(testItemId);
        assertNotNull(updated);
        assertEquals(newBid, updated.getCurrentHighestBid(), 0.01,
                "Giá cao nhất phải được cập nhật thành " + newBid);
        assertEquals(bidder, updated.getCurrentHighestBidder(),
                "Người dẫn đầu phải được cập nhật");
    }

    @Test
    public void testPlaceBidById_nonExistentItem() {
        boolean result = itemDAO.placeBidById(-999, 500.0, "someone");
        assertFalse(result, "Phải trả về false khi itemId không tồn tại");
    }

    // =========================================================================
    // TEST 5: updateStatus() — chuyển trạng thái
    // =========================================================================
    @Test
    public void testUpdateStatus_toFinished() {
        itemDAO.updateStatus(testItemId, Item.Status.FINISHED);

        Item updated = itemDAO.getItemById(testItemId);
        assertNotNull(updated);
        assertEquals(Item.Status.FINISHED, updated.getStatus(),
                "Trạng thái phải chuyển sang FINISHED");
    }

    // =========================================================================
    // TEST 6: activatePendingItems() — kích hoạt phiên OPEN → RUNNING
    //
    // FIX Bug 5: Đây là hàm mới thêm vào để Scheduler có thể chuyển
    //   phiên OPEN sang RUNNING khi đến start_time.
    // =========================================================================
    @Test
    public void testActivatePendingItems_opensBecomeRunning() throws SQLException {
        // Tạo 1 item OPEN với start_time trong quá khứ (đã đến giờ bắt đầu)
        String openItemName = "OpenItem_" + System.currentTimeMillis();
        int openItemId = -1;

        String sql = "INSERT INTO items (name, description, starting_price, current_highest_bid, "
                + "highest_bidder, status, start_time, end_time, seller_id) "
                + "VALUES (?, 'Test OPEN item', 50.0, 50.0, 'Chưa có', 'OPEN', "
                + "DATE_SUB(NOW(), INTERVAL 10 MINUTE), "   // start_time = 10 phút trước
                + "(UNIX_TIMESTAMP() + 3600) * 1000, 1)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, openItemName);
            pstmt.executeUpdate();
            var keys = pstmt.getGeneratedKeys();
            if (keys.next()) openItemId = keys.getInt(1);
        }

        // Gọi activatePendingItems() — phải chuyển item vừa tạo sang RUNNING
        int activated = itemDAO.activatePendingItems();
        assertTrue(activated >= 1, "Phải kích hoạt ít nhất 1 phiên OPEN");

        // Kiểm tra DB đã cập nhật
        Item updated = itemDAO.getItemById(openItemId);
        assertNotNull(updated);
        assertEquals(Item.Status.RUNNING, updated.getStatus(),
                "Item OPEN có start_time trong quá khứ phải được chuyển sang RUNNING");

        // Dọn dẹp item phụ
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement p = conn.prepareStatement("DELETE FROM items WHERE id = ?")) {
            p.setInt(1, openItemId);
            p.executeUpdate();
        }
    }
}