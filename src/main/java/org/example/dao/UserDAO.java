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
        /*
         * Schema bảng users:
         *   balance      — số dư thực tế của user
         *   held_balance — số tiền đang bị đóng băng (đang giữ cho bid hiện tại)
         *
         * available_balance = balance - held_balance
         * Khi bid: chỉ tăng held_balance, KHÔNG trừ balance
         * Khi bị vượt: giảm held_balance (release)
         * Khi thắng phiên: mới trừ balance thật và giảm held_balance
         */
        String sql = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT UNIQUE NOT NULL,"
                + "password TEXT NOT NULL,"
                + "role TEXT NOT NULL,"
                + "balance REAL DEFAULT 0.0,"
                + "held_balance REAL DEFAULT 0.0"  // HOLD BALANCE: tiền đang bị đóng băng
                + ");";

        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.execute();
            System.out.println("[DB] Đã kiểm tra/tạo bảng users.");
            /*
             * Nếu bảng users đã tồn tại từ phiên bản cũ,
             * CREATE TABLE IF NOT EXISTS sẽ không tự thêm cột.
             * Vì vậy cần migration thủ công cho cả hai cột.
             */
            ensureBalanceColumnExists();
            ensureHeldBalanceColumnExists(); // HOLD BALANCE: migration thêm cột held_balance
            // Tạo tài khoản admin mặc định nếu chưa tồn tại
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

        boolean hasBalanceColumn = false;

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             ResultSet rs = checkStmt.executeQuery()) {

            while (rs.next()) {
                String columnName = rs.getString("name");

                if ("balance".equalsIgnoreCase(columnName)) {
                    hasBalanceColumn = true;
                    break;
                }
            }

        } catch (SQLException e) {
            System.err.println("[DB] Lỗi kiểm tra cột balance: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (!hasBalanceColumn) {
            try (PreparedStatement alterStmt = conn.prepareStatement(alterSql)) {
                alterStmt.executeUpdate();
                System.out.println("[DB] Đã thêm cột balance vào bảng users.");
            } catch (SQLException e) {
                System.err.println("[DB] Lỗi thêm cột balance: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * HOLD BALANCE: Migration tự động — thêm cột held_balance vào bảng users nếu chưa có.
     * Cần thiết khi server upgrade từ phiên bản cũ chưa có cột này.
     */
    private void ensureHeldBalanceColumnExists() {
        String checkSql = "PRAGMA table_info(users)";
        String alterSql = "ALTER TABLE users ADD COLUMN held_balance REAL DEFAULT 0.0";

        Connection conn = DatabaseConnection.getConnection();

        boolean hasHeldBalanceColumn = false;

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             ResultSet rs = checkStmt.executeQuery()) {

            while (rs.next()) {
                String columnName = rs.getString("name");
                if ("held_balance".equalsIgnoreCase(columnName)) {
                    hasHeldBalanceColumn = true;
                    break;
                }
            }

        } catch (SQLException e) {
            System.err.println("[DB] Lỗi kiểm tra cột held_balance: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (!hasHeldBalanceColumn) {
            try (PreparedStatement alterStmt = conn.prepareStatement(alterSql)) {
                alterStmt.executeUpdate();
                System.out.println("[DB] Đã thêm cột held_balance vào bảng users.");
            } catch (SQLException e) {
                System.err.println("[DB] Lỗi thêm cột held_balance: " + e.getMessage());
                e.printStackTrace();
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

    /**
     * Lấy số dư thực tế (balance) của User.
     * Lưu ý: đây là balance GỐC, không phải available_balance.
     * Dùng getAvailableBalance() để biết user còn có thể dùng bao nhiêu.
     * @return số dư, hoặc -1 nếu lỗi / không tìm thấy user
     */
    public double getBalance(String username) {
        String sql = "SELECT balance FROM users WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi getBalance: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * HOLD BALANCE: Lấy số tiền đang bị đóng băng (held) của User.
     * held_balance là tổng tiền đang được giữ cho các bid hiện tại.
     * @return held_balance, hoặc -1 nếu lỗi
     */
    public double getHeldBalance(String username) {
        String sql = "SELECT held_balance FROM users WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("held_balance");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi getHeldBalance: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * HOLD BALANCE: Tính available_balance = balance - held_balance.
     * Đây là số tiền user thực sự có thể dùng để bid.
     * @return available_balance, hoặc -1 nếu lỗi
     */
    public double getAvailableBalance(String username) {
        String sql = "SELECT balance - held_balance AS available_balance FROM users WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("available_balance");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi getAvailableBalance: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * HOLD BALANCE: Đóng băng (hold) một khoản tiền khi user đặt giá.
     * Chỉ tăng held_balance, KHÔNG trừ balance.
     * Đảm bảo held_balance không vượt quá balance.
     * @param username tên user
     * @param amount   số tiền cần hold (phải > 0)
     * @return true nếu thành công
     */
    public boolean holdBalance(String username, double amount) {
        // Chỉ hold nếu available_balance đủ: balance - held_balance >= amount
        String sql = "UPDATE users "
                + "SET held_balance = held_balance + ? "
                + "WHERE username = ? AND (balance - held_balance) >= ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, username);
            pstmt.setDouble(3, amount);
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                System.out.println("[DB] holdBalance thất bại: " + username
                        + " không đủ available_balance cho amount=" + amount);
            }
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi holdBalance: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * HOLD BALANCE: Giải phóng (release/unlock) tiền đang bị đóng băng.
     * Dùng khi: user bị người khác vượt giá → held về 0 cho bid đó.
     * Đảm bảo held_balance không âm (không release quá số đang hold).
     * @param username tên user
     * @param amount   số tiền cần release (phải > 0)
     * @return true nếu thành công
     */
    public boolean releaseHeldBalance(String username, double amount) {
        // Đảm bảo held_balance không âm sau khi release
        String sql = "UPDATE users "
                + "SET held_balance = MAX(0, held_balance - ?) "
                + "WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi releaseHeldBalance: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * HOLD BALANCE: Thực hiện trừ tiền thật khi user THẮNG phiên đấu giá.
     * Chạy trong 1 DB transaction atomic:
     *   1. Trừ balance đi bidAmount
     *   2. Giảm held_balance đi bidAmount (release hold)
     * Nếu bất kỳ bước nào fail → rollback toàn bộ.
     *
     * @param username  tên winner
     * @param bidAmount số tiền trúng thầu (phải > 0)
     * @return true nếu giao dịch thành công
     */
    public boolean deductBalanceOnWin(String username, double bidAmount) {
        // Trừ balance và release held cùng lúc trong 1 câu SQL atomic
        // balance = balance - bidAmount
        // held_balance = MAX(0, held_balance - bidAmount)  ← phòng edge case
        String sql = "UPDATE users "
                + "SET balance = balance - ?, "
                + "    held_balance = MAX(0, held_balance - ?) "
                + "WHERE username = ? AND balance >= ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, bidAmount);
            pstmt.setDouble(2, bidAmount);
            pstmt.setString(3, username);
            pstmt.setDouble(4, bidAmount); // guard: balance phải >= bidAmount
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                System.err.println("[DB] deductBalanceOnWin thất bại: " + username
                        + " không đủ balance=" + bidAmount);
            }
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi deductBalanceOnWin: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Nạp tiền vào tài khoản (cộng thêm vào số dư hiện có).
     * Cũng dùng để cộng tiền cho seller khi phiên đấu giá kết thúc.
     * @param amount phải > 0
     */
    public boolean updateBalance(String username, double amount) {
        String sql = "UPDATE users SET balance = balance + ? WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi updateBalance: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Đổi mật khẩu — xác thực mật khẩu cũ trước, sau đó lưu hash mới.
     * @return true nếu thành công, false nếu sai mật khẩu cũ hoặc lỗi DB
     */
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        // Xác thực mật khẩu cũ
        User user = login(username, oldPassword);
        if (user == null) {
            return false;
        }

        String hashedNewPassword = hashPassword(newPassword);
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hashedNewPassword);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi changePassword: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

