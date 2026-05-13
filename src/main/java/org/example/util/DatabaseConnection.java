package org.example.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // Biến lưu trữ kết nối duy nhất
    private static Connection connection = null;

    // Đường dẫn tới file database SQLite
    private static final String URL = "jdbc:sqlite:auction.db";

    // Hàm gọi kết nối
    public static Connection getConnection() {
        try {
            // Nếu chưa có kết nối nào hoặc kết nối đã đóng, tạo kết nối mới
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(URL);
                System.out.println("Đã kết nối SQLite thành công!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
    }
}