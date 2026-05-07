package org.example.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton cung cấp một Connection dùng chung cho toàn bộ ứng dụng.
 * FIX BUG 3: Sử dụng volatile + synchronized để đảm bảo thread-safe
 * theo pattern "Double-Checked Locking" chuẩn (Java 5+).
 */
public class DatabaseConnection {

    // volatile đảm bảo mọi thread thấy giá trị mới nhất ngay lập tức
    private static volatile Connection connection = null;

    private static final String URL = "jdbc:sqlite:auction.db";

    // Ngăn tạo instance từ bên ngoài
    private DatabaseConnection() {}

    public static Connection getConnection() {
        // Kiểm tra lần 1 — không cần lock nếu đã có connection (fast path)
        if (connection == null || isClosed()) {
            // Chỉ 1 thread được vào vùng synchronized tại 1 thời điểm
            synchronized (DatabaseConnection.class) {
                // Kiểm tra lần 2 — phòng thread thứ 2 chờ và vào sau
                if (connection == null || isClosed()) {
                    try {
                        connection = DriverManager.getConnection(URL);
                        System.out.println("[DB] Kết nối SQLite thành công!");
                    } catch (SQLException e) {
                        System.err.println("[DB] Lỗi kết nối: " + e.getMessage());
                        throw new RuntimeException("Không thể kết nối cơ sở dữ liệu!", e);
                    }
                }
            }
        }
        return connection;
    }

    /** Helper kiểm tra connection đã đóng, tránh SQLException lan ra ngoài */
    private static boolean isClosed() {
        try {
            return connection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }
}