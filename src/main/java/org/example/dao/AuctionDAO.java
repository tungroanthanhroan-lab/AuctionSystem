package org.example.dao;

import org.example.model.Auction;
import org.example.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {

    /**
     * Tạo bảng auctions nếu chưa tồn tại.
     * FIX BUG 13: Dùng DatabaseConnection Singleton thay vì tạo connection mới.
     */
    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS auctions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "item_id INTEGER, "
                + "start_time TEXT, "
                + "end_time TEXT, "
                + "status TEXT DEFAULT 'OPEN', "
                + "current_highest_bid REAL DEFAULT 0, "
                + "current_leader TEXT, "
                + "version INTEGER DEFAULT 0, "
                + "FOREIGN KEY(item_id) REFERENCES items(id))";

        Connection conn = DatabaseConnection.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DB] Đã kiểm tra/tạo bảng auctions.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Mở một phiên đấu giá mới (Dành cho Admin/Seller).
     * FIX BUG 13: Dùng DatabaseConnection Singleton.
     */
    public boolean startAuction(int itemId, String endTime) {
        String sql = "INSERT INTO auctions(item_id, start_time, end_time, status, version) "
                   + "VALUES(?, datetime('now'), ?, 'OPEN', 0)";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            pstmt.setString(2, endTime);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * FIX BUG 1: Thêm phương thức này — AuctionService.loadActiveAuctionsFromDB() gọi nó.
     * Lấy tất cả phiên đấu giá đang OPEN hoặc RUNNING từ DB.
     */
    public List<Auction> getAllOpenAuctions() {
        List<Auction> list = new ArrayList<>();
        String sql = "SELECT * FROM auctions WHERE status = 'OPEN' OR status = 'RUNNING'";
        Connection conn = DatabaseConnection.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Auction auction = new Auction(
                        rs.getInt("id"),
                        rs.getInt("item_id"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        rs.getString("status")
                );
                auction.setCurrentHighestBid(rs.getDouble("current_highest_bid"));
                auction.setVersion(rs.getInt("version"));
                list.add(auction);
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi load auctions: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * FIX BUG 1: Thêm phương thức này — AuctionService.placeBid() gọi nó.
     * Cập nhật giá đấu theo Optimistic Locking: chỉ thành công nếu version khớp.
     */
    public boolean updateBidWithOptimisticLock(String auctionId, String bidderName,
                                               double amount, int expectedVersion) {
        String sql = "UPDATE auctions "
                   + "SET current_highest_bid = ?, current_leader = ?, version = version + 1 "
                   + "WHERE id = ? AND version = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, bidderName);
            pstmt.setString(3, auctionId);
            pstmt.setInt(4, expectedVersion);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0; 
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi updateBidWithOptimisticLock: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Đóng phiên đấu giá trong DB.
     */
    public boolean closeAuction(String auctionId) {
        String sql = "UPDATE auctions SET status = 'FINISHED' WHERE id = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Tạo phiên đấu giá mới — INSERT vào DB và set generated ID trở lại Auction object.
     * Giữ lại từ nhánh feature/rebuild-models.
     */
    public boolean insertAuction(Auction auction) {
        String sql = "INSERT INTO auctions(item_id, start_time, end_time, status, current_highest_bid, version) "
                   + "VALUES(?, ?, ?, 'OPEN', ?, 0)";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int itemId = (auction.getItem() != null) ? auction.getItem().getId() : 0;
            pstmt.setInt(1, itemId);
            pstmt.setString(2, auction.getStartTime() != null
                    ? auction.getStartTime().toString() : java.time.LocalDateTime.now().toString());
            pstmt.setString(3, auction.getEndTime() != null
                    ? auction.getEndTime().toString() : "");
            pstmt.setDouble(4, auction.getCurrentHighestBid());

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        String generatedId = String.valueOf(generatedKeys.getLong(1));
                        auction.setAuctionId(generatedId);
                        System.out.println("[DB] Đã tạo phiên đấu giá mới — id=" + generatedId);
                    }
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi insertAuction: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy danh sách các phiên đấu giá đang MỞ (tên cũ giữ lại để tương thích).
     */
    public List<Auction> getActiveAuctions() {
        return getAllOpenAuctions();
    }
}