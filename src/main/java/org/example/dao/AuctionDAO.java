package org.example.dao;

import org.example.model.Auction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {
    private String url = "jdbc:sqlite:auction.db";

    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS auctions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +       // Hoặc TEXT nếu bạn dùng String
                "item_id INTEGER, " +
                "start_time TEXT, " +
                "end_time TEXT, " +
                "status TEXT DEFAULT 'OPEN', " +
                "current_highest_bid REAL DEFAULT 0, " +         // Thêm: Lưu giá
                "current_leader TEXT, " +                        // Thêm: Lưu người dẫn đầu
                "version INTEGER DEFAULT 0, " +                  // Thêm: Lock đa luồng
                "FOREIGN KEY(item_id) REFERENCES items(id))";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Mở một phiên đấu giá mới (Dành cho Admin/Seller)
    public boolean startAuction(int itemId, String endTime) {
        String sql = "INSERT INTO auctions(item_id, start_time, end_time, status) VALUES(?, datetime('now'), ?, 'OPEN')";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            pstmt.setString(2, endTime);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // Lấy danh sách các phiên đấu giá đang MỞ (Sử dụng List và ArrayList ở đây nè)
    public List<Auction> getActiveAuctions() {
        List<Auction> activeAuctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = 'OPEN'";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                activeAuctions.add(new Auction(
                        rs.getInt("id"),
                        rs.getInt("item_id"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return activeAuctions;
    }
}