package org.example.dao;

import org.example.model.Auction;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * AuctionDAO — handles all DB interactions for the auctions table.
 *
 */
public class AuctionDAO {

    /** Create the auctions table if it does not exist. */
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

    /** Open a new auction session (for Admin/Seller). */
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
     * Load all OPEN or RUNNING auctions from DB, including their Item data (via JOIN).
     **/
    public List<Auction> getAllOpenAuctions() {
        List<Auction> list = new ArrayList<>();
        String sql = "SELECT a.*, i.title, i.description, i.starting_price, i.current_price, "
                + "i.end_time AS item_end_time, i.seller_id, i.status AS item_status "
                + "FROM auctions a "
                + "JOIN items i ON a.item_id = i.id "
                + "WHERE a.status = 'OPEN' OR a.status = 'RUNNING'";
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
                        rs.getString("item_end_time"),
                        rs.getInt("seller_id"),
                        rs.getString("item_status")
                );

                auction.setItem(item);
                auction.setCurrentHighestBid(rs.getDouble("current_highest_bid"));
                auction.setVersion(rs.getInt("version"));

                String leaderName = rs.getString("current_leader");
                if (leaderName != null && !leaderName.isEmpty()) {
                    auction.setCurrentLeader(new Bidder(leaderName));
                }

                list.add(auction);
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi load auctions: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Update bid using Optimistic Locking — succeeds only if version matches.
     * Returns false (0 rows affected) if another thread already incremented the version.
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
     * HOLD BALANCE — Atomically: update auction (Optimistic Lock) + hold new bidder's money
     * + release previous leader's held money. All in one DB transaction.
     *
     * Steps:
     *  1. UPDATE auctions WHERE version matches (Optimistic Lock)
     *  2. Increase held_balance for newBidder (if available_balance >= amount)
     *  3. Decrease held_balance for prevLeader (release their earlier hold)
     * If any step fails → full rollback.
     */
    public boolean placeBidWithHold(String auctionId,
                                    String newBidder, double newAmount, int expectedVersion,
                                    String prevLeader, double prevAmount) {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);

            // Step 1: Update auction with Optimistic Locking
            String updateAuctionSql = "UPDATE auctions "
                    + "SET current_highest_bid = ?, current_leader = ?, version = version + 1, status = 'RUNNING' "
                    + "WHERE id = ? AND version = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
                pstmt.setDouble(1, newAmount);
                pstmt.setString(2, newBidder);
                pstmt.setString(3, auctionId);
                pstmt.setInt(4, expectedVersion);
                int rows = pstmt.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    System.out.println("[DB] placeBidWithHold: Optimistic lock conflict — auction=" + auctionId
                            + " expectedVersion=" + expectedVersion);
                    return false;
                }
            }

            // Step 2: Hold new bidder's money (only if available balance >= amount)
            String holdSql = "UPDATE users "
                    + "SET held_balance = held_balance + ? "
                    + "WHERE username = ? AND (balance - held_balance) >= ?";
            try (PreparedStatement pstmt = conn.prepareStatement(holdSql)) {
                pstmt.setDouble(1, newAmount);
                pstmt.setString(2, newBidder);
                pstmt.setDouble(3, newAmount);
                int rows = pstmt.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    System.out.println("[DB] placeBidWithHold: " + newBidder
                            + " insufficient available_balance for amount=" + newAmount);
                    return false;
                }
            }

            // Step 3: Release previous leader's held money (if applicable)
            if (prevLeader != null && !prevLeader.isEmpty() && !prevLeader.equals(newBidder) && prevAmount > 0) {
                String releaseSql = "UPDATE users "
                        + "SET held_balance = MAX(0, held_balance - ?) "
                        + "WHERE username = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(releaseSql)) {
                    pstmt.setDouble(1, prevAmount);
                    pstmt.setString(2, prevLeader);
                    pstmt.executeUpdate();
                    System.out.println("[DB] placeBidWithHold: Released " + prevAmount
                            + " for " + prevLeader + " (outbid)");
                }
            }

            conn.commit();
            System.out.println("[DB] placeBidWithHold: Success — auction=" + auctionId
                    + " | bidder=" + newBidder + " | amount=" + newAmount);
            return true;

        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("[DB] Rollback error: " + rollbackEx.getMessage());
            }
            System.err.println("[DB] Lỗi placeBidWithHold: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("[DB] Error restoring autoCommit: " + e.getMessage());
            }
        }
    }

    /**
     * HOLD BALANCE — Get winner and seller info when closing a session.
     * Returns [winnerUsername, bidAmount, sellerUsername] or null if no bids were placed.
     */
    public String[] getWinnerInfo(String auctionId) {
        String sql = "SELECT a.current_leader, a.current_highest_bid, u.username AS seller_username "
                + "FROM auctions a "
                + "JOIN items i ON a.item_id = i.id "
                + "JOIN users u ON i.seller_id = u.id "
                + "WHERE a.id = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String leader = rs.getString("current_leader");
                    if (leader == null || leader.isEmpty()) {
                        return null;
                    }
                    return new String[]{
                            leader,
                            String.valueOf(rs.getDouble("current_highest_bid")),
                            rs.getString("seller_username")
                    };
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi getWinnerInfo: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Create item and auction in one DB transaction.
     * Returns the new Auction (with generated ID set) or null on failure.
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
                    insertItemSql, Statement.RETURN_GENERATED_KEYS)) {
                itemStmt.setString(1, title);
                itemStmt.setString(2, description);
                itemStmt.setDouble(3, startingPrice);
                itemStmt.setDouble(4, startingPrice);
                itemStmt.setString(5, endTime);
                itemStmt.setInt(6, sellerId);

                if (itemStmt.executeUpdate() == 0) {
                    conn.rollback();
                    return null;
                }
                try (ResultSet keys = itemStmt.getGeneratedKeys()) {
                    if (keys.next()) itemId = keys.getInt(1);
                    else { conn.rollback(); return null; }
                }
            }

            int auctionId;
            try (PreparedStatement auctionStmt = conn.prepareStatement(
                    insertAuctionSql, Statement.RETURN_GENERATED_KEYS)) {
                auctionStmt.setInt(1, itemId);
                auctionStmt.setString(2, endTime);
                auctionStmt.setDouble(3, startingPrice);

                if (auctionStmt.executeUpdate() == 0) {
                    conn.rollback();
                    return null;
                }
                try (ResultSet keys = auctionStmt.getGeneratedKeys()) {
                    if (keys.next()) auctionId = keys.getInt(1);
                    else { conn.rollback(); return null; }
                }
            }

            conn.commit();

            Auction auction = new Auction(
                    String.valueOf(auctionId), null, null,
                    org.example.model.AuctionStatus.OPEN, startingPrice,
                    null, null, null
            );

            Item item = new Item(itemId, title, description, startingPrice, startingPrice,
                    endTime, sellerId, "OPEN");
            auction.setItem(item);
            auction.setVersion(0);

            return auction;

        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            e.printStackTrace();
            return null;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    /** Close auction in DB (set status = FINISHED). */
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

    /** Check if a given user is the seller/owner of an auction session. */
    public boolean isAuctionOwner(String auctionId, int userId) {
        String sql = "SELECT i.seller_id FROM auctions a "
                + "JOIN items i ON a.item_id = i.id WHERE a.id = ?";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("seller_id") == userId;
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi isAuctionOwner: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /** Get all auctions created by a specific seller (for MY_AUCTIONS command). */
    public List<Auction> getAuctionsBySellerId(int sellerId) {
        List<Auction> list = new ArrayList<>();
        String sql = "SELECT a.*, i.title, i.description, i.starting_price, i.current_price, "
                + "i.end_time AS item_end_time, i.seller_id, i.status AS item_status "
                + "FROM auctions a "
                + "JOIN items i ON a.item_id = i.id "
                + "WHERE i.seller_id = ? ORDER BY a.id DESC";
        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, sellerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Auction auction = new Auction(
                            rs.getInt("id"), rs.getInt("item_id"),
                            rs.getString("start_time"), rs.getString("end_time"),
                            rs.getString("status")
                    );
                    Item item = new Item(
                            rs.getInt("item_id"), rs.getString("title"),
                            rs.getString("description"), rs.getDouble("starting_price"),
                            rs.getDouble("current_price"), rs.getString("item_end_time"),
                            rs.getInt("seller_id"), rs.getString("item_status")
                    );
                    auction.setItem(item);
                    auction.setCurrentHighestBid(rs.getDouble("current_highest_bid"));
                    auction.setVersion(rs.getInt("version"));
                    list.add(auction);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] Lỗi getAuctionsBySellerId: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Insert a new auction row and set the generated ID back onto the Auction object.
     * Used by AuctionService.createAuction() (itemId-based creation flow).
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

    /** Alias for getAllOpenAuctions() — kept for backward compatibility. */
    public List<Auction> getActiveAuctions() {
        return getAllOpenAuctions();
    }
}