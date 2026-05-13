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
 * Mỗi test tự tạo dữ liệu mẫu vào DB trước khi chạy (@BeforeEach)
 * và tự dọn dẹp sau khi xong (@AfterEach) để không ảnh hưởng dữ liệu thật.
 */
public class ItemDAOTest {

    private ItemDAO itemDAO;

    // ID của item test — lưu lại để xóa sau mỗi test
    private int testItemId = -1;

    // Tên item test dùng timestamp để tránh trùng khi chạy nhiều lần
    private String testItemName;

    @BeforeEach
    public void setUp() throws SQLException {
        itemDAO = new ItemDAO();
        testItemName = "TestItem_" + System.currentTimeMillis();

        // Tạo 1 item mẫu vào bảng items (trạng thái RUNNING, giá khởi điểm 100)
        String sql = "INSERT INTO items (name, description, starting_price, current_highest_bid, "
                + "highest_bidder, status, end_time, seller_id) "
                + "VALUES (?, 'Mô tả test', 100.0, 100.0, 'Chưa có', 'RUNNING', "
                + "(UNIX_TIMESTAMP() + 3600) * 1000, 1)";

        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql,
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, testItemName);
            pstmt.executeUpdate();

            // Lấy ID vừa được tạo để dùng trong các test
            var keys = pstmt.getGeneratedKeys();
            if (keys.next()) {
                testItemId = keys.getInt(1);
            }
        }
    }

    @AfterEach
    public void tearDown() throws SQLException {
        // Dọn dẹp: xóa item test và lịch sử đấu giá liên quan
        if (testItemId == -1) return;
        Connection conn = DatabaseConnection.getConnection();

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

    // =========================================================================
    // TEST 1: getActiveItems() — phải trả về danh sách không rỗng và
    //         chứa item vừa tạo (đang RUNNING)
    // =========================================================================
    @Test
    public void testGetActiveItems_returnsList() {
        List<Item> items = itemDAO.getActiveItems();

        // Danh sách không được null
        assertNotNull(items, "getActiveItems() không được trả về null");

        // Phải tìm thấy item test vừa tạo trong danh sách
        boolean found = items.stream()
                .anyMatch(item -> item.getName().equals(testItemName));
        assertTrue(found, "Phải tìm thấy item '" + testItemName + "' trong danh sách active");
    }

    // =========================================================================
    // TEST 2: getItemById() — lấy đúng item theo ID
    // =========================================================================
    @Test
    public void testGetItemById_existingItem() {
        Item item = itemDAO.getItemById(testItemId);

        // Phải tìm thấy item
        assertNotNull(item, "getItemById() phải trả về item khi ID tồn tại");

        // Kiểm tra các trường dữ liệu
        assertEquals(testItemName, item.getName(),
                "Tên item phải khớp với dữ liệu đã tạo");
        assertEquals(100.0, item.getStartingPrice(), 0.01,
                "Giá khởi điểm phải là 100.0");
        assertEquals(Item.Status.RUNNING, item.getStatus(),
                "Trạng thái phải là RUNNING");
    }

    @Test
    public void testGetItemById_notFound() {
        // ID âm chắc chắn không tồn tại trong DB
        Item item = itemDAO.getItemById(-999);

        assertNull(item, "getItemById() phải trả về null khi ID không tồn tại");
    }

    // =========================================================================
    // TEST 3: getItemByName() — lấy đúng item theo tên
    // =========================================================================
    @Test
    public void testGetItemByName_existingItem() {
        Item item = itemDAO.getItemByName(testItemName);

        assertNotNull(item, "getItemByName() phải trả về item khi tên tồn tại");
        assertEquals(testItemId, item.getId(), "ID item phải khớp");
    }

    @Test
    public void testGetItemByName_notFound() {
        Item item = itemDAO.getItemByName("TênKhôngTồnTại_xyz_999");

        assertNull(item, "getItemByName() phải trả về null khi tên không tồn tại");
    }

    // =========================================================================
    // TEST 4: placeBid() — cập nhật giá mới lên DB
    // =========================================================================
    @Test
    public void testPlaceBid_updatesPrice() {
        double newBid = 250.0;
        String bidder = "bidder_test";

        boolean result = itemDAO.placeBid(String.valueOf(testItemId), newBid, bidder);

        // Hàm phải trả về true (cập nhật thành công)
        assertTrue(result, "placeBid() phải trả về true khi cập nhật thành công");

        // Đọc lại từ DB và kiểm tra giá đã thay đổi
        Item updated = itemDAO.getItemById(testItemId);
        assertNotNull(updated);
        assertEquals(newBid, updated.getCurrentHighestBid(), 0.01,
                "Giá cao nhất phải được cập nhật thành " + newBid);
        assertEquals(bidder, updated.getCurrentHighestBidder(),
                "Người dẫn đầu phải được cập nhật");
    }

    // =========================================================================
    // TEST 5: updateStatus() — thay đổi trạng thái item
    // =========================================================================
    @Test
    public void testUpdateStatus_toFinished() {
        itemDAO.updateStatus(testItemId, Item.Status.FINISHED);

        Item updated = itemDAO.getItemById(testItemId);
        assertNotNull(updated);
        assertEquals(Item.Status.FINISHED, updated.getStatus(),
                "Trạng thái phải chuyển sang FINISHED");
    }
}