package org.example.dao;

import org.example.util.DatabaseConnection; // Nhập thư viện Singleton
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.example.model.User;
import java.sql.ResultSet;

public class UserDAO {
    // Tạo bảng nếu chưa tồn tại bảng nào
    public void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT UNIQUE NOT NULL,"
                + "password TEXT NOT NULL,"
                + "role TEXT NOT NULL"
                + ");";

        // Lấy kết nối dùng chung
        Connection conn = DatabaseConnection.getConnection();

        // Chỉ cho PreparedStatement vào trong ngoặc try để chạy xong thì tự hủy
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.execute();
            System.out.println("Đã kiểm tra/tạo bảng users.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean registerUser(String username, String password, String role) {
        String sql = "INSERT INTO users(username, password, role) VALUES(?,?,?)";

        Connection conn = DatabaseConnection.getConnection();

        // 2. Cho xe rùa chở 3 món hàng (username, pass, role) chạy xuống DB
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, role);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Lỗi đăng ký: Có thể username đã tồn tại.");
            return false;
        }
    }

    public User login(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        Connection conn = DatabaseConnection.getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Nếu tìm thấy user, đóng gói vào đối tượng User
                return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("role")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Trả về null nếu đăng nhập thất bại
    }
}
