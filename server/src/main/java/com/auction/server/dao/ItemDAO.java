package com.auction.server.dao;

import com.auction.server.models.Item;
import com.auction.server.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // Lấy tất cả sản phẩm đang trong trạng thái mở đấu giá
    public List<Item> getActiveItems() {
        List<Item> items = new ArrayList<>();
        // Giả sử trong DB có bảng items với cột status = 'OPEN'
        String sql = "SELECT * FROM items WHERE status = 'OPEN'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                double startingPrice = rs.getDouble("starting_price");

                Item item = new Item(id, name, startingPrice);

                // Nếu có lưu giá cao nhất hiện tại trong DB, ta cập nhật luôn
                double currentHighestBid = rs.getDouble("current_highest_bid");
                if (currentHighestBid > startingPrice) {
                    item.setCurrentHighestBid(currentHighestBid);
                    item.setCurrentHighestBidder(rs.getString("highest_bidder_username"));
                }

                items.add(item);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi tải danh sách sản phẩm: " + e.getMessage());
        }
        return items;
    }
}