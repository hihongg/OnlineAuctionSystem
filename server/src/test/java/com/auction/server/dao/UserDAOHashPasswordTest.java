package com.auction.server.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test THUẦN JAVA cho UserDAO — KHÔNG cần kết nối Database.
 *
 * Tại sao cần file test riêng này?
 *   - UserDAOTest, BidDAOTest, ItemDAOTest đều cần MySQL thật (Integration Test).
 *   - Trên CI, MySQL service container có thể khởi động chậm hoặc gặp sự cố.
 *   - File này test logic thuần Java (hashPassword) — luôn chạy được
 *     ngay cả khi DB chưa sẵn sàng, đảm bảo CI không bao giờ bị "0 tests".
 *
 * Đây là ví dụ về "Unit Test thực sự" (pure unit test):
 *   Không phụ thuộc I/O, không phụ thuộc DB, không phụ thuộc mạng.
 *   → Chạy nhanh, luôn ổn định, dễ debug.
 */
@DisplayName("UserDAO — Hash Password (Pure Java, không cần DB)")
public class UserDAOHashPasswordTest {

    // =========================================================================
    // TEST 1: SHA-256 cho cùng input phải cho cùng output
    //
    // Tính chất cơ bản của hàm hash: deterministic (tất định).
    // =========================================================================
    @Test
    @DisplayName("Hash cùng input 2 lần → phải ra cùng kết quả")
    public void testHashPassword_samInputSameOutput() {
        String hash1 = UserDAO.hashPassword("mySecret123");
        String hash2 = UserDAO.hashPassword("mySecret123");

        assertEquals(hash1, hash2,
                "Hash của cùng 1 chuỗi phải luôn cho kết quả giống nhau (deterministic)");
    }

    // =========================================================================
    // TEST 2: Hai input khác nhau phải cho output khác nhau
    //
    // Tính chất collision resistance — xác suất xảy ra va chạm SHA-256 cực nhỏ.
    // =========================================================================
    @Test
    @DisplayName("Hash 2 password khác nhau → phải ra kết quả khác nhau")
    public void testHashPassword_differentInputDifferentOutput() {
        String hash1 = UserDAO.hashPassword("password123");
        String hash2 = UserDAO.hashPassword("password456");

        assertNotEquals(hash1, hash2,
                "Hai password khác nhau phải cho hash khác nhau");
    }

    // =========================================================================
    // TEST 3: Kết quả là chuỗi hex 64 ký tự (SHA-256 = 256 bit = 32 byte = 64 hex chars)
    // =========================================================================
    @Test
    @DisplayName("Kết quả hash phải là chuỗi hex 64 ký tự")
    public void testHashPassword_outputFormat() {
        String hash = UserDAO.hashPassword("anyInput");

        assertNotNull(hash, "Hash không được là null");
        assertEquals(64, hash.length(),
                "SHA-256 hash phải có đúng 64 ký tự hex (256 bit)");
        assertTrue(hash.matches("[0-9a-f]{64}"),
                "Hash chỉ được chứa ký tự hex lowercase [0-9a-f]");
    }

    // =========================================================================
    // TEST 4: Giá trị hash của 'admin123' phải khớp với giá trị trong SQL schema.
    //
    // Đây là test quan trọng nhất: đảm bảo file auction_db.sql và UserDAO.java
    // dùng CÙNG thuật toán hash → admin có thể đăng nhập sau khi chạy schema.
    //
    // Nếu test này fail → password admin trong SQL sai → admin không login được.
    // =========================================================================
    @Test
    @DisplayName("Hash 'admin123' phải khớp với giá trị đã lưu trong auction_db.sql")
    public void testHashPassword_admin123_matchesSqlSchema() {
        // Giá trị này phải khớp CHÍNH XÁC với cột password trong INSERT của auction_db.sql
        String expectedHash = "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9";

        String actualHash = UserDAO.hashPassword("admin123");

        assertEquals(expectedHash, actualHash,
                "Hash của 'admin123' phải khớp với giá trị trong auction_db.sql. "
                        + "Nếu fail → sửa lại INSERT trong SQL hoặc thuật toán hash bị thay đổi.");
    }

    // =========================================================================
    // TEST 5: Password trống vẫn phải cho ra hash hợp lệ (không throw exception)
    //
    // Mặc dù ClientHandler từ chối password rỗng, hàm hash vẫn phải an toàn.
    // =========================================================================
    @Test
    @DisplayName("Hash chuỗi rỗng không được throw exception")
    public void testHashPassword_emptyString_noException() {
        assertDoesNotThrow(() -> {
            String hash = UserDAO.hashPassword("");
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }, "hashPassword(\"\") không được ném exception");
    }

    // =========================================================================
    // TEST 6: Chữ hoa và chữ thường phải cho hash khác nhau (case-sensitive)
    //
    // Đảm bảo "Password" ≠ "password" → quan trọng cho security.
    // =========================================================================
    @Test
    @DisplayName("Hash phân biệt chữ hoa/thường (case-sensitive)")
    public void testHashPassword_caseSensitive() {
        String lowerHash = UserDAO.hashPassword("password");
        String upperHash = UserDAO.hashPassword("Password");
        String allCapsHash = UserDAO.hashPassword("PASSWORD");

        assertNotEquals(lowerHash, upperHash, "\"password\" và \"Password\" phải có hash khác nhau");
        assertNotEquals(lowerHash, allCapsHash, "\"password\" và \"PASSWORD\" phải có hash khác nhau");
    }
}