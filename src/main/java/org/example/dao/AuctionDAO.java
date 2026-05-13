package org.example.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {
    private String url = "jdbc:sqlite:auction.db";

    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS auctions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "item_id INTEGER, " +
                "start_time TEXT, " +
                "end_time TEXT, " +
                "status TEXT DEFAULT 'OPEN', " +
                "FOREIGN KEY(item_id) REFERENCES items(id))";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Mở một phiên đấu giá mới (Dành cho Admin/Seller)
    public boolean startAuction(int itemId, String endTime) {
        String sql = "INSERT INTO auctions(item_id, start_time, end_time, status) VALUES(?, datetime('now'), ?, 'OPEN')";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, itemId);
            pstmt.setString(2, endTime);

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Lấy danh sách các phiên đấu giá đang MỞ
    public List<String> getActiveAuctions() {
        List<String> activeAuctions = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = 'OPEN'";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                int itemId = rs.getInt("item_id");
                String startTime = rs.getString("start_time");
                String endTime = rs.getString("end_time");
                String status = rs.getString("status");

                activeAuctions.add(
                        id + " - Item " + itemId +
                                " - Bắt đầu: " + startTime +
                                " - Kết thúc: " + endTime +
                                " - Trạng thái: " + status
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return activeAuctions;
    }
}