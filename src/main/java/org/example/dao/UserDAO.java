package org.example.dao;

import org.example.util.DatabaseConnection;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.example.model.User;

/**
 * UserDAO — handles all DB interactions for the users table.
 **/

public class UserDAO {

    /**
     * Create users table if it does not exist.
     * Also runs migration to add balance and held_balance columns for existing DBs.
     */
    public void createTableIfNotExists() {
        /*
         * Schema:
         *   balance      — user's real balance
         *   held_balance — money locked for active bids (available = balance - held_balance)
         */
        String sql = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT UNIQUE NOT NULL,"
                + "password TEXT NOT NULL,"
                + "role TEXT NOT NULL,"
                + "balance REAL DEFAULT 0.0,"
                + "held_balance REAL DEFAULT 0.0"
                + ");";

        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.execute();
            System.out.println("[DB] Đã kiểm tra/tạo bảng users.");
            ensureBalanceColumnExists();
            ensureHeldBalanceColumnExists();
            createDefaultAdminIfNotExists();
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi tạo bảng users: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureBalanceColumnExists() {
        String checkSql = "PRAGMA table_info(users)";
        String alterSql = "ALTER TABLE users ADD COLUMN balance REAL DEFAULT 0.0";
        Connection conn = DatabaseConnection.getConnection();
        boolean hasColumn = false;
        try (PreparedStatement stmt = conn.prepareStatement(checkSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                if ("balance".equalsIgnoreCase(rs.getString("name"))) {
                    hasColumn = true;
                    break;
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi kiểm tra cột balance: " + e.getMessage());
            return;
        }
        if (!hasColumn) {
            try (PreparedStatement stmt = conn.prepareStatement(alterSql)) {
                stmt.executeUpdate();
                System.out.println("[DB] Đã thêm cột balance vào bảng users.");
            } catch (SQLException e) {
                System.err.println("[DB] Lỗi thêm cột balance: " + e.getMessage());
            }
        }
    }

    private void ensureHeldBalanceColumnExists() {
        String checkSql = "PRAGMA table_info(users)";
        String alterSql = "ALTER TABLE users ADD COLUMN held_balance REAL DEFAULT 0.0";
        Connection conn = DatabaseConnection.getConnection();
        boolean hasColumn = false;
        try (PreparedStatement stmt = conn.prepareStatement(checkSql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                if ("held_balance".equalsIgnoreCase(rs.getString("name"))) {
                    hasColumn = true;
                    break;
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi kiểm tra cột held_balance: " + e.getMessage());
            return;
        }
        if (!hasColumn) {
            try (PreparedStatement stmt = conn.prepareStatement(alterSql)) {
                stmt.executeUpdate();
                System.out.println("[DB] Đã thêm cột held_balance vào bảng users.");
            } catch (SQLException e) {
                System.err.println("[DB] Lỗi thêm cột held_balance: " + e.getMessage());
            }
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
                if (rs.next() && rs.getInt(1) > 0) adminExists = true;
            }
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

    public User login(String username, String password) {
        String hashedPassword = hashPassword(password);
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        Connection conn = DatabaseConnection.getConnection();
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

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 không khả dụng", e);
        }
    }

    public double getBalance(String username) {
        String sql = "SELECT balance FROM users WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi getBalance: " + e.getMessage());
        }
        return -1;
    }

    public double getHeldBalance(String username) {
        String sql = "SELECT held_balance FROM users WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("held_balance");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi getHeldBalance: " + e.getMessage());
        }
        return -1;
    }

    public double getAvailableBalance(String username) {
        String sql = "SELECT balance - held_balance AS available_balance FROM users WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("available_balance");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi getAvailableBalance: " + e.getMessage());
        }
        return -1;
    }

    public boolean holdBalance(String username, double amount) {
        String sql = "UPDATE users SET held_balance = held_balance + ? "
                + "WHERE username = ? AND (balance - held_balance) >= ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, username);
            pstmt.setDouble(3, amount);
            int rows = pstmt.executeUpdate();
            if (rows == 0)
                System.out.println("[DB] holdBalance thất bại: " + username + " insufficient balance");
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi holdBalance: " + e.getMessage());
            return false;
        }
    }

    public boolean releaseHeldBalance(String username, double amount) {
        String sql = "UPDATE users SET held_balance = MAX(0, held_balance - ?) WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi releaseHeldBalance: " + e.getMessage());
            return false;
        }
    }

    /**
     * Atomically deduct winner's balance and release their held_balance in one SQL statement.
     * Called when an auction session is closed.
     */
    public boolean deductBalanceOnWin(String username, double bidAmount) {
        String sql = "UPDATE users "
                + "SET balance = balance - ?, "
                + "    held_balance = MAX(0, held_balance - ?) "
                + "WHERE username = ? AND balance >= ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, bidAmount);
            pstmt.setDouble(2, bidAmount);
            pstmt.setString(3, username);
            pstmt.setDouble(4, bidAmount);
            int rows = pstmt.executeUpdate();
            if (rows == 0)
                System.err.println("[DB] deductBalanceOnWin thất bại: " + username + " insufficient balance");
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi deductBalanceOnWin: " + e.getMessage());
            return false;
        }
    }

    /** Deposit or credit money to a user (also used to pay seller on auction close). */
    public boolean updateBalance(String username, double amount) {
        String sql = "UPDATE users SET balance = balance + ? WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi updateBalance: " + e.getMessage());
            return false;
        }
    }

    /** Change password — verifies old password first, then saves new hash. */
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        User user = login(username, oldPassword);
        if (user == null) return false;
        String hashedNewPassword = hashPassword(newPassword);
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hashedNewPassword);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi changePassword: " + e.getMessage());
            return false;
        }
    }
}