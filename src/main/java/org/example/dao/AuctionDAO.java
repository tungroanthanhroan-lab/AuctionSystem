package org.example.dao;

import org.example.model.Auction;
import org.example.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.example.model.Item;
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
        String sql = "SELECT a.*, i.title, i.description, i.starting_price, i.current_price, i.end_time, i.seller_id, i.status AS item_status " +
                "FROM auctions a " +
                "JOIN items i ON a.item_id = i.id " +
                "WHERE a.status = 'OPEN' OR a.status = 'RUNNING'";
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

                Item item = new Item(
                        rs.getInt("item_id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getDouble("starting_price"),
                        rs.getDouble("current_price"),
                        rs.getString("end_time"),
                        rs.getInt("seller_id"),
                        rs.getString("item_status")
                );

                auction.setItem(item);
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
     *
     * SQL: UPDATE auctions SET current_highest_bid=?, current_leader=?, version=version+1
     *      WHERE id=? AND version=?
     * Nếu version đã bị người khác tăng trước → rowsAffected = 0 → trả về false.
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
            return rowsAffected > 0; // 0 nghĩa là version đã bị thay đổi trước đó (conflict)
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi updateBidWithOptimisticLock: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Tạo item mới rồi tạo phiên đấu giá cho item đó.
     *
     * Flow:
     * 1. Insert vào bảng items.
     * 2. Lấy itemId vừa tạo.
     * 3. Insert vào bảng auctions.
     * 4. Lấy auctionId vừa tạo.
     * 5. Trả về Auction để AuctionService đưa vào activeAuctions.
     */
    public Auction createAuctionWithNewItem(String title,
                                            String description,
                                            double startingPrice,
                                            String endTime,
                                            int sellerId) {
        Connection conn = DatabaseConnection.getConnection();

        String insertItemSql = "INSERT INTO items(title, description, starting_price, current_price, end_time, seller_id, status) "
                + "VALUES(?, ?, ?, ?, ?, ?, 'OPEN')";

        String insertAuctionSql = "INSERT INTO auctions(item_id, start_time, end_time, status, current_highest_bid, version) "
                + "VALUES(?, datetime('now'), ?, 'OPEN', ?, 0)";

        try {
            conn.setAutoCommit(false);

            int itemId;

            try (PreparedStatement itemStmt = conn.prepareStatement(
                    insertItemSql,
                    Statement.RETURN_GENERATED_KEYS
            )) {
                itemStmt.setString(1, title);
                itemStmt.setString(2, description);
                itemStmt.setDouble(3, startingPrice);
                itemStmt.setDouble(4, startingPrice);
                itemStmt.setString(5, endTime);
                itemStmt.setInt(6, sellerId);

                int itemRows = itemStmt.executeUpdate();

                if (itemRows == 0) {
                    conn.rollback();
                    return null;
                }

                try (ResultSet generatedKeys = itemStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        itemId = generatedKeys.getInt(1);
                    } else {
                        conn.rollback();
                        return null;
                    }
                }
            }

            int auctionId;

            try (PreparedStatement auctionStmt = conn.prepareStatement(
                    insertAuctionSql,
                    Statement.RETURN_GENERATED_KEYS
            )) {
                auctionStmt.setInt(1, itemId);
                auctionStmt.setString(2, endTime);
                auctionStmt.setDouble(3, startingPrice);

                int auctionRows = auctionStmt.executeUpdate();

                if (auctionRows == 0) {
                    conn.rollback();
                    return null;
                }

                try (ResultSet generatedKeys = auctionStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        auctionId = generatedKeys.getInt(1);
                    } else {
                        conn.rollback();
                        return null;
                    }
                }
            }

            conn.commit();

            Auction auction = new Auction(
                    auctionId,
                    itemId,
                    "",
                    endTime,
                    "OPEN"
            );

            /*
             * Set item vào auction để VIEW_ITEMS lấy được title ngay,
             * không phải chờ restart server/load lại từ DB.
             */
            Item item = new Item(
                    itemId,
                    title,
                    description,
                    startingPrice,
                    startingPrice,
                    endTime,
                    sellerId,
                    "OPEN"
            );

            auction.setItem(item);
            auction.setCurrentHighestBid(startingPrice);
            auction.setVersion(0);

            return auction;

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackException) {
                rollbackException.printStackTrace();
            }

            e.printStackTrace();
            return null;

        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
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
     * Lấy danh sách các phiên đấu giá đang MỞ (tên cũ giữ lại để tương thích).
     * FIX BUG 13: Dùng DatabaseConnection Singleton.
     */
    public List<Auction> getActiveAuctions() {
        return getAllOpenAuctions();
    }
}