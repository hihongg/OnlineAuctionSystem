-- ============================================================
-- Hệ thống đấu giá trực tuyến — Script khởi tạo CSDL
-- Chạy lệnh: mysql -u root -p < auction_db.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS auction_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE auction_db;

-- ------------------------------------------------------------
-- Bảng người dùng
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
                                     id       INT          AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(50)  NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,          -- TODO: hash trước khi lưu (bcrypt/MD5)
    email    VARCHAR(100),
    role     ENUM('BIDDER', 'SELLER', 'ADMIN') NOT NULL DEFAULT 'BIDDER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- Admin mặc định để test
INSERT IGNORE INTO users (username, password, email, role)
VALUES ('admin', 'admin123', 'admin@auction.com', 'ADMIN');

-- ------------------------------------------------------------
-- Bảng sản phẩm đấu giá
-- Tên cột chuẩn: highest_bidder (không phải highest_bidder_username)
-- Khớp với: BidDAO.java, ItemDAO.java, BidDAOTest.java, ItemDAOTest.java
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS items (
                                     id                  INT           AUTO_INCREMENT PRIMARY KEY,
                                     name                VARCHAR(100)  NOT NULL,
    description         TEXT,
    starting_price      DOUBLE        NOT NULL DEFAULT 0,
    current_highest_bid DOUBLE        NOT NULL DEFAULT 0,
    highest_bidder      VARCHAR(50)   DEFAULT 'Chưa có',   -- ← tên cột thống nhất
    status              ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED')
    NOT NULL DEFAULT 'OPEN',
    start_time          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    end_time            BIGINT        DEFAULT 0,            -- lưu dạng milliseconds
    seller_id           INT,
    FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE SET NULL
    );

-- Dữ liệu mẫu để test (trạng thái RUNNING, kết thúc sau 1 giờ)
INSERT IGNORE INTO items
    (name, description, starting_price, current_highest_bid, highest_bidder,
     status, end_time, seller_id)
VALUES
    ('Laptop Dell XPS 15', 'Máy tính xách tay cao cấp, i7-12700H, 16GB RAM',
     500.0, 500.0, 'Chưa có', 'RUNNING',
     (UNIX_TIMESTAMP() + 3600) * 1000, 1),

    ('Bàn phím cơ Keychron Q1', 'Bàn phím cơ full-aluminum, switch Gateron Pro',
     80.0, 80.0, 'Chưa có', 'RUNNING',
     (UNIX_TIMESTAMP() + 7200) * 1000, 1);

-- ------------------------------------------------------------
-- Bảng lịch sử đặt giá
-- Dùng cho biểu đồ giá realtime (BidDAO.getBidHistory)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bid_history (
                                           id         INT       AUTO_INCREMENT PRIMARY KEY,
                                           item_id    INT       NOT NULL,
                                           username   VARCHAR(50) NOT NULL,
    bid_amount DOUBLE    NOT NULL,
    bid_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
    );

-- Index để getBidHistory() chạy nhanh (query theo item_id + order by time)
CREATE INDEX IF NOT EXISTS idx_bid_history_item
    ON bid_history (item_id, bid_time);