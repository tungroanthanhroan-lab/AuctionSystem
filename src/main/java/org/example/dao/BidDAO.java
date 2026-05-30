package org.example.dao;

import org.example.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * BidDAO — handles all DB interactions for the bids table.
 *
 * MERGE NOTE: Master version kept. Rebuild dropped getBidHistory() because the rebuild
 * branch had no UI to display it. The UI's BiddingController (from master) calls
 * GET_BID_HISTORY which requires this method.
 */
public class BidDAO {

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
     * Get full bid history for an auction, most recent first.
     * Joins with users to include username.
     *
     * MERGE NOTE: This method only exists in master — required by the UI's
     * GET_BID_HISTORY command in ClientHandler and BiddingController.
     *
     * @return List of String[] rows: [userId, username, bidAmount, bidTime]
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