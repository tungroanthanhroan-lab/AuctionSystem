package org.example.dao;

import org.example.util.DatabaseConnection;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.example.model.User;

public class UserDAO {

    /**
     * Tạo bảng users nếu chưa tồn tại.
     * FIX BUG 2: Thêm dấu phẩy thiếu giữa cột role và balance.
     */
    public void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT UNIQUE NOT NULL,"
                + "password TEXT NOT NULL,"
                + "role TEXT NOT NULL,"           // FIX: thêm dấu phẩy ở đây
                + "balance REAL DEFAULT 0.0"
                + ");";

        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.execute();
            System.out.println("[DB] Đã kiểm tra/tạo bảng users.");
            //Tạo tài khoản admin mặc định nếu chưa tồn tại
            createDefaultAdminIfNotExists();
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi tạo bảng users: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public void createDefaultAdminIfNotExists() {
        String checkSql = "SELECT COUNT(*) FROM users WHERE username = ?";
        String insertSql = "INSERT INTO users(username, password, role, balance) VALUES(?, ?, ?, ?)";
        String updateSql = "UPDATE users SET password = ?, role = ? WHERE username = ?";

        Connection conn = DatabaseConnection.getConnection();

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, "admin");

            boolean adminExists = false;

            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    adminExists = true;
                }
            }

            /*
             * UserDAO.login() so sánh password đã hash SHA-256.
             * Vì vậy admin mặc định cũng phải lưu password đã hash,
             * không được lưu plain text "admin123".
             */
            String hashedAdminPassword = hashPassword("admin123");

            if (adminExists) {
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, hashedAdminPassword);
                    updateStmt.setString(2, "ADMIN");
                    updateStmt.setString(3, "admin");

                    updateStmt.executeUpdate();

                    System.out.println("[DB] Đã cập nhật tài khoản admin mặc định: admin/admin123");
                }

                return;
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, "admin");
                insertStmt.setString(2, hashedAdminPassword);
                insertStmt.setString(3, "ADMIN");
                insertStmt.setDouble(4, 0.0);

                insertStmt.executeUpdate();

                System.out.println("[DB] Đã tạo tài khoản admin mặc định: admin/admin123");
            }

        } catch (SQLException e) {
            System.err.println("[DB] Lỗi tạo/cập nhật admin mặc định: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Đăng ký user mới.
     * FIX BUG 10: Hash password bằng SHA-256 trước khi lưu vào DB.
     */
    public boolean registerUser(String username, String password, String role) {
        String hashedPassword = hashPassword(password);
        String sql = "INSERT INTO users(username, password, role) VALUES(?,?,?)";

        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, role);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("[Auth] Đăng ký thất bại: Username '" + username + "' có thể đã tồn tại.");
            return false;
        }
    }

    /**
     * Đăng nhập.
     * FIX BUG 10: So sánh password đã hash.
     * FIX BUG 14: Đóng ResultSet trong try-with-resources.
     */
    public User login(String username, String password) {
        String hashedPassword = hashPassword(password);
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        Connection conn = DatabaseConnection.getConnection();

        // FIX BUG 14: Dùng try-with-resources cho cả PreparedStatement và ResultSet
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password"),
                            rs.getString("role")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[Auth] Lỗi đăng nhập: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Hash mật khẩu bằng SHA-256.
     * FIX BUG 10: Không lưu plaintext password.
     * Lưu ý production nên dùng BCrypt với salt — SHA-256 ở đây là cải thiện tối thiểu.
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 luôn tồn tại trong Java SE — không thực sự xảy ra
            throw new RuntimeException("SHA-256 không khả dụng", e);
        }
    }
}
