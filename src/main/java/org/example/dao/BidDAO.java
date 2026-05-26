package org.example.dao;

import org.example.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class BidDAO {

    /**
     * Tạo bảng bids nếu chưa tồn tại.
     * FIX BUG 13: Dùng DatabaseConnection Singleton thay vì tạo connection mới.
     */
    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS bids ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "auction_id INTEGER, "
                + "user_id INTEGER, "
                + "bid_amount REAL, "
                + "bid_time TEXT, "
                + "FOREIGN KEY(auction_id) REFERENCES auctions(id), "
                + "FOREIGN KEY(user_id) REFERENCES users(id))";

        Connection conn = DatabaseConnection.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DB] Đã kiểm tra/tạo bảng bids.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ghi một lượt đặt giá vào DB.
     * FIX BUG 13: Dùng DatabaseConnection Singleton.
     */
    public boolean placeBid(int auctionId, int userId, double amount) {
        String sql = "INSERT INTO bids(auction_id, user_id, bid_amount, bid_time) "
                   + "VALUES(?, ?, ?, datetime('now'))";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            pstmt.setInt(2, userId);
            pstmt.setDouble(3, amount);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi ghi bid: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy giá cao nhất hiện tại của một phiên.
     * FIX BUG 13: Dùng DatabaseConnection Singleton.
     * FIX: Dùng try-with-resources để đóng ResultSet đúng cách.
     */
    public double getMaxBid(int auctionId) {
        String sql = "SELECT MAX(bid_amount) FROM bids WHERE auction_id = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi getMaxBid: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Lấy toàn bộ lịch sử đặt giá của một phiên đấu giá, sắp xếp mới nhất trước.
     * JOIN với bảng users để lấy username.
     *
     * @param auctionId id phiên cần truy vấn
     * @return List các mảng String gồm [userId, username, bidAmount, bidTime]
     */
    public List<String[]> getBidHistory(int auctionId) {
        List<String[]> result = new ArrayList<>();
        String sql = "SELECT b.user_id, u.username, b.bid_amount, b.bid_time "
                   + "FROM bids b "
                   + "LEFT JOIN users u ON b.user_id = u.id "
                   + "WHERE b.auction_id = ? "
                   + "ORDER BY b.bid_time DESC";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String[] row = {
                        String.valueOf(rs.getInt("user_id")),
                        rs.getString("username") != null ? rs.getString("username") : "unknown",
                        String.valueOf(rs.getDouble("bid_amount")),
                        rs.getString("bid_time") != null ? rs.getString("bid_time") : ""
                    };
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi getBidHistory: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }
}