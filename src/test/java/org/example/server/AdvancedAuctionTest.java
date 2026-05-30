package org.example.server;

import org.example.exception.AuctionClosedException;
import org.example.exception.InvalidBidException;
import org.example.model.Auction;
import org.example.model.AuctionStatus;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.observer.AuctionNotifier;
import org.example.service.AutoBidConfig;
import org.example.service.AuctionService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test cho tính năng Auto-Bidding và Anti-Sniping trong AuctionService.
 *
 * KEY DESIGN:
 *   All bids go through auctionService.placeBid() — NOT auctionNormal.placeBid() directly.
 *   This is required because triggerAutoBids() is only called from inside AuctionService.placeBid().
 *   Calling Auction.placeBid() directly bypasses the entire auto-bid orchestration.
 *
 *   To make auctionService.placeBid() work without a real DB:
 *     - StubAuctionDAO: all write operations return true (no-op)
 *     - StubUserDAO: getAvailableBalance() always returns 999_999 (balance check always passes)
 */
public class AdvancedAuctionTest {

    private Auction auctionNormal;   // Phiên thông thường (còn 1 tiếng)
    private Auction auctionNearEnd;  // Phiên sắp hết giờ (còn 30 giây)

    private Bidder alice;
    private Bidder bob;
    private Bidder charlie;

    private AuctionNotifier notifier;
    private AuctionService auctionService;

    @BeforeEach
    public void setUp() {
        alice   = new Bidder(1, "Alice",   "pass", 5000.0);
        bob     = new Bidder(2, "Bob",     "pass", 6000.0);
        charlie = new Bidder(3, "Charlie", "pass", 7000.0);

        notifier = new AuctionNotifier();

        Item item = new Item(1, "iPhone 15", "Brand new", 100.0, 100.0,
                "2026-12-31", 1, "OPEN");

        // Normal session — 1 hour left (outside snipe window)
        auctionNormal = new Auction(
                "A001", item, null, AuctionStatus.RUNNING,
                100.0,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusHours(1),
                notifier
        );

        // Near-end session — 30 seconds left (inside 60-second snipe window)
        auctionNearEnd = new Auction(
                "A002", item, null, AuctionStatus.RUNNING,
                100.0,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusSeconds(30),
                notifier
        );

        // StubUserDAO always returns a large available balance so balance checks pass.
        // StubAuctionDAO makes all DB writes succeed as no-ops.
        auctionService = new AuctionService(
                new StubAuctionDAO(),
                notifier,
                new StubUserDAO()
        );

        auctionService.addAuctionForTest(auctionNormal);
        auctionService.addAuctionForTest(auctionNearEnd);
    }

    @AfterEach
    public void tearDown() {
        notifier.shutdown();
    }

    // ════════════════════════════════════════════════════════════════
    // AUTO-BIDDING TESTS
    // ════════════════════════════════════════════════════════════════

    /**
     * When Alice bids manually, Bob's registered auto-bid must fire automatically.
     * Bob: maxBid=300, increment=20 → auto-bids to 170 (150 + 20).
     *
     * IMPORTANT: bid goes through auctionService.placeBid(), not auction.placeBid() directly,
     * because triggerAutoBids() is only called from AuctionService.placeBid().
     */
    @Test
    public void testAutoBid_TriggeredAfterManualBid() throws InterruptedException {
        AutoBidConfig bobConfig = new AutoBidConfig(bob, 300.0, 20.0);
        auctionService.registerAutoBid("A001", bobConfig);

        // Route through the service — this triggers the auto-bid thread
        auctionService.placeBid("A001", "Alice", 150.0);

        // Give the auto-bid background thread time to execute
        Thread.sleep(500);

        Auction auction = auctionService.getAuction("A001");
        assertEquals(170.0, auction.getCurrentHighestBid(), 0.001,
                "Bob phải tự động đặt giá 170 (150 + increment 20)");
        assertEquals("Bob", auction.getCurrentLeader().getUsername(),
                "Bob phải là người dẫn đầu sau auto-bid");
    }

    /**
     * Auto-bid must NOT exceed the registered maxBid.
     * Bob: maxBid=160, increment=20. Alice bids 150. Next would be 170 > 160 → blocked.
     */
    @Test
    public void testAutoBid_DoesNotExceedMaxBid() throws InterruptedException {
        AutoBidConfig bobConfig = new AutoBidConfig(bob, 160.0, 20.0);
        auctionService.registerAutoBid("A001", bobConfig);

        auctionService.placeBid("A001", "Alice", 150.0);
        Thread.sleep(300);

        Auction auction = auctionService.getAuction("A001");
        assertEquals(150.0, auction.getCurrentHighestBid(), 0.001,
                "Giá phải giữ nguyên vì Bob không đủ maxBid (170 > 160)");
        assertEquals("Alice", auction.getCurrentLeader().getUsername(),
                "Alice vẫn phải là người dẫn đầu");
    }

    /**
     * Cancel auto-bid: after cancellation, Bob should not automatically counter Alice.
     */
    @Test
    public void testCancelAutoBid() throws InterruptedException {
        AutoBidConfig bobConfig = new AutoBidConfig(bob, 300.0, 20.0);
        auctionService.registerAutoBid("A001", bobConfig);

        auctionService.cancelAutoBid("A001", bob);
        assertEquals(0, auctionService.getAutoBidCount("A001"),
                "Phải không còn auto-bid nào sau khi hủy");

        auctionService.placeBid("A001", "Alice", 150.0);
        Thread.sleep(300);

        Auction auction = auctionService.getAuction("A001");
        assertEquals("Alice", auction.getCurrentLeader().getUsername(),
                "Alice vẫn dẫn đầu vì Bob đã hủy auto-bid");
    }

    /**
     * Registering auto-bid with maxBid lower than current price → InvalidBidException.
     */
    @Test
    public void testRegisterAutoBid_Fail_MaxBidTooLow() {
        // Set current price to 200 via the service
        auctionService.placeBid("A001", "Alice", 200.0);

        AutoBidConfig badConfig = new AutoBidConfig(bob, 150.0, 10.0);
        assertThrows(InvalidBidException.class,
                () -> auctionService.registerAutoBid("A001", badConfig),
                "Phải ném InvalidBidException khi maxBid < currentHighestBid");
    }

    /**
     * Registering auto-bid on a FINISHED session → AuctionClosedException.
     */
    @Test
    public void testRegisterAutoBid_Fail_AuctionClosed() {
        auctionNormal.setStatus(AuctionStatus.FINISHED);

        AutoBidConfig config = new AutoBidConfig(bob, 300.0, 20.0);
        assertThrows(AuctionClosedException.class,
                () -> auctionService.registerAutoBid("A001", config),
                "Phải ném AuctionClosedException khi phiên đã đóng");
    }

    // ════════════════════════════════════════════════════════════════
    // ANTI-SNIPING TESTS
    // Anti-snipe logic lives in AuctionService.placeBid — tested through the service.
    // ════════════════════════════════════════════════════════════════

    /**
     * Bidding inside the snipe window (< 60 seconds left) must extend endTime.
     */
    @Test
    public void testAntiSnipe_ExtendsEndTime() {
        LocalDateTime originalEndTime = auctionNearEnd.getEndTime();

        // Bid via the service — anti-snipe extension happens inside AuctionService.placeBid()
        auctionService.placeBid("A002", "Alice", 150.0);

        LocalDateTime newEndTime = auctionService.getAuction("A002").getEndTime();

        assertTrue(newEndTime.isAfter(originalEndTime),
                "endTime phải được gia hạn sau khi có bid trong cửa sổ cuối phiên");

        System.out.println("endTime cũ  : " + originalEndTime);
        System.out.println("endTime mới : " + newEndTime);
    }

    /**
     * Bidding when plenty of time remains (outside snipe window) → endTime unchanged.
     */
    @Test
    public void testAntiSnipe_NoExtensionWhenNotNearEnd() {
        LocalDateTime originalEndTime = auctionNormal.getEndTime();

        // Session has 1 hour left — outside the 60-second snipe window
        auctionService.placeBid("A001", "Alice", 150.0);

        assertEquals(originalEndTime, auctionService.getAuction("A001").getEndTime(),
                "endTime không được thay đổi khi bid còn cách xa thời điểm kết thúc");
    }

    // ════════════════════════════════════════════════════════════════
    // STUBS — avoid a real DB connection in unit tests
    // ════════════════════════════════════════════════════════════════

    /** AuctionDAO stub: all writes succeed as no-ops; getAllOpenAuctions returns empty list. */
    private static class StubAuctionDAO extends org.example.dao.AuctionDAO {
        @Override public void createTable() {}
        @Override public boolean startAuction(int itemId, String endTime) { return true; }
        @Override public java.util.List<org.example.model.Auction> getAllOpenAuctions() { return new java.util.ArrayList<>(); }
        @Override public boolean updateBidWithOptimisticLock(String auctionId, String bidderName, double amount, int expectedVersion) { return true; }
        @Override public boolean placeBidWithHold(String auctionId, String newBidder, double newAmount, int expectedVersion, String prevLeader, double prevAmount) { return true; }
        @Override public boolean closeAuction(String auctionId) { return true; }
        @Override public boolean insertAuction(org.example.model.Auction auction) { return true; }
    }

    /**
     * UserDAO stub: getAvailableBalance() always returns 999_999 so balance checks
     * inside AuctionService.placeBid() always pass. All other methods are no-ops.
     */
    private static class StubUserDAO extends org.example.dao.UserDAO {
        @Override public void createTableIfNotExists() {}
        @Override public boolean registerUser(String u, String p, String r) { return true; }
        @Override public org.example.model.User login(String u, String p) { return null; }
        @Override public double getBalance(String username)          { return 999_999.0; }
        @Override public double getHeldBalance(String username)      { return 0.0; }
        @Override public double getAvailableBalance(String username) { return 999_999.0; }
        @Override public boolean holdBalance(String username, double amount) { return true; }
        @Override public boolean releaseHeldBalance(String username, double amount) { return true; }
        @Override public boolean deductBalanceOnWin(String username, double amount) { return true; }
        @Override public boolean updateBalance(String username, double amount) { return true; }
        @Override public boolean changePassword(String u, String oldP, String newP) { return true; }
    }
}
