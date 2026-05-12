package org.example.dao;

import java.sql.*;

public class BidDAO {
    private String url = "jdbc:sqlite:auction.db";

    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS bids (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "auction_id INTEGER, " +
                "user_id INTEGER, " +
                "bid_amount REAL, " +
                "bid_time TEXT, " +
                "FOREIGN KEY(auction_id) REFERENCES auctions(id), " +
                "FOREIGN KEY(user_id) REFERENCES users(id))";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Đặt giá
    public boolean placeBid(int auctionId, int userId, double amount) {
        String sql = "INSERT INTO bids(auction_id, user_id, bid_amount, bid_time) VALUES(?, ?, ?, datetime('now'))";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            pstmt.setInt(2, userId);
            pstmt.setDouble(3, amount);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // Lấy giá cao nhất hiện tại của một phiên
    public double getMaxBid(int auctionId) {
        String sql = "SELECT MAX(bid_amount) FROM bids WHERE auction_id = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, auctionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }
}