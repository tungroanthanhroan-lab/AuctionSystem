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
 * THIẾT KẾ CỐT LÕI:
 * Tất cả các lượt đặt giá (bid) đều đi qua auctionService.placeBid() — KHÔNG gọi trực tiếp auctionNormal.placeBid().
 * Điều này là bắt buộc vì triggerAutoBids() chỉ được gọi từ bên trong AuctionService.placeBid().
 * Gọi trực tiếp Auction.placeBid() sẽ bỏ qua toàn bộ quy trình điều phối tự động đặt giá.
 *
 * Để auctionService.placeBid() hoạt động mà không cần cơ sở dữ liệu thật:
 * - StubAuctionDAO: tất cả các thao tác ghi đều trả về true (không gây tác động thực tế - no-op)
 * - StubUserDAO: getAvailableBalance() luôn trả về 999_999 (bước kiểm tra số dư luôn thành công)
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

        // Phiên thông thường — còn 1 tiếng (nằm ngoài khung giờ chống bắn tỉa/snipe window)
        auctionNormal = new Auction(
                "A001", item, null, AuctionStatus.RUNNING,
                100.0,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusHours(1),
                notifier
        );

        // Phiên sắp kết thúc — còn 30 giây (nằm trong khung giờ chống bắn tỉa 60 giây cuối)
        auctionNearEnd = new Auction(
                "A002", item, null, AuctionStatus.RUNNING,
                100.0,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusSeconds(30),
                notifier
        );

        // StubUserDAO luôn trả về số dư khả dụng lớn để các bước kiểm tra số dư đều thành công.
        // StubAuctionDAO cho phép tất cả các thao tác ghi DB thành công mà không làm gì cả (no-ops).
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
    // CÁC TEST CHO TÍNH NĂNG TỰ ĐỘNG ĐẶT GIÁ (AUTO-BIDDING)
    // ════════════════════════════════════════════════════════════════

    /**
     * Khi Alice đặt giá thủ công, lệnh tự động đặt giá đã đăng ký của Bob phải tự động kích hoạt.
     * Bob: giá tối đa (maxBid) = 300, bước giá (increment) = 20 → tự động nâng giá lên 170 (150 + 20).
     *
     * QUAN TRỌNG: lượt đặt giá đi qua auctionService.placeBid(), không gọi trực tiếp auction.placeBid(),
     * vì triggerAutoBids() chỉ được gọi từ AuctionService.placeBid().
     */
    @Test
    public void testAutoBid_TriggeredAfterManualBid() throws InterruptedException {
        AutoBidConfig bobConfig = new AutoBidConfig(bob, 300.0, 20.0);
        auctionService.registerAutoBid("A001", bobConfig);

        // Đi qua service — điều này sẽ kích hoạt luồng (thread) tự động đặt giá
        auctionService.placeBid("A001", "Alice", 150.0);

        // Dành thời gian để luồng chạy ngầm auto-bid thực thi
        Thread.sleep(500);

        Auction auction = auctionService.getAuction("A001");
        assertEquals(170.0, auction.getCurrentHighestBid(), 0.001,
                "Bob phải tự động đặt giá 170 (150 + increment 20)");
        assertEquals("Bob", auction.getCurrentLeader().getUsername(),
                "Bob phải là người dẫn đầu sau auto-bid");
    }

    /**
     * Auto-bid KHÔNG ĐƯỢC vượt quá mức giá tối đa (maxBid) đã đăng ký.
     * Bob: maxBid=160, increment=20. Alice đặt 150. Lần nâng giá tiếp theo sẽ là 170 > 160 → bị chặn.
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
     * Khi hai người tham gia đều đăng ký auto-bid với cùng mức giá tối đa,
     * người nào đăng ký SỚM HƠN sẽ giành vị trí dẫn đầu cuối cùng.
     *
     * Logic:
     * Alice đặt giá 150 thủ công → kích hoạt chuỗi auto-bid.
     * Bob đăng ký trước, Charlie đăng ký sau — cả hai đều có maxBid=500, increment=20.
     * Mỗi vòng: người đăng ký sớm nhất đủ điều kiện sẽ nhận được lượt auto-bid tiếp theo.
     * Bob luôn được ưu tiên, nên Bob sẽ luôn phản hồi lại Charlie.
     * Leo thang giá: 170(Bob) → 190(Charlie) → 210(Bob) → ... → 490(Bob) → Charlie cần
     * đặt 510 nhưng 510 > 500 → Charlie dừng lại. Bob thắng ở mức 490.
     *
     * Test này cho 3 giây để chuỗi hoàn tất (thông thường nó xử lý xong trong < 500 ms).
     */
    @Test
    public void testAutoBid_PriorityByRegistrationTime() throws InterruptedException {
        // Bob đăng ký trước, Charlie đăng ký sau một chút — cùng maxBid và increment
        AutoBidConfig bobConfig = new AutoBidConfig(bob, 500.0, 20.0);
        Thread.sleep(20); // đảm bảo mốc thời gian đăng ký (registeredAt) là khác biệt
        AutoBidConfig charlieConfig = new AutoBidConfig(charlie, 500.0, 20.0);

        auctionService.registerAutoBid("A001", bobConfig);
        auctionService.registerAutoBid("A001", charlieConfig);

        // Lượt đặt giá thủ công của Alice bắt đầu chuỗi tự động đặt giá
        auctionService.placeBid("A001", "Alice", 150.0);

        // Cho đủ thời gian để toàn bộ chuỗi leo thang giá hoàn tất
        Thread.sleep(3000);

        Auction auction = auctionService.getAuction("A001");

        // Bob đăng ký sớm hơn → Bob chiến thắng ở vị trí cuối cùng trong chuỗi
        assertEquals("Bob", auction.getCurrentLeader().getUsername(),
                "Bob đăng ký trước nên thắng cuối cùng khi cả hai có cùng maxBid");

        // Giá chắc chắn đã leo thang qua nhiều bước, vượt xa mức 150 ban đầu của Alice
        assertTrue(auction.getCurrentHighestBid() > 170.0,
                "Giá phải leo thang qua nhiều vòng auto-bid, không dừng ở 170");

        System.out.println("[Priority Test] Giá cuối: " + auction.getCurrentHighestBid()
                + " | Người thắng: " + auction.getCurrentLeader().getUsername());
    }

    /**
     * Hủy tự động đặt giá: sau khi hủy, Bob sẽ không tự động đặt giá đáp trả Alice.
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
     * Đăng ký auto-bid với maxBid thấp hơn giá hiện tại → sẽ ném ngoại lệ InvalidBidException.
     */
    @Test
    public void testRegisterAutoBid_Fail_MaxBidTooLow() {
        // Thiết lập giá hiện tại lên 200 thông qua service
        auctionService.placeBid("A001", "Alice", 200.0);

        AutoBidConfig badConfig = new AutoBidConfig(bob, 150.0, 10.0);
        assertThrows(InvalidBidException.class,
                () -> auctionService.registerAutoBid("A001", badConfig),
                "Phải ném InvalidBidException khi maxBid < currentHighestBid");
    }

    /**
     * Đăng ký auto-bid vào một phiên đã KẾT THÚC → sẽ ném ngoại lệ AuctionClosedException.
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
    // CÁC TEST CHO TÍNH NĂNG CHỐNG BẮN TỈA (ANTI-SNIPING)
    // Logic chống bắn tỉa nằm ở AuctionService.placeBid — được test thông qua service.
    // ════════════════════════════════════════════════════════════════

    /**
     * Đặt giá trong khung giờ chống bắn tỉa (< 60 giây cuối) sẽ phải kéo dài thêm thời gian kết thúc (endTime).
     */
    @Test
    public void testAntiSnipe_ExtendsEndTime() {
        LocalDateTime originalEndTime = auctionNearEnd.getEndTime();

        // Đặt giá qua service — việc gia hạn thời gian chống bắn tỉa diễn ra trong AuctionService.placeBid()
        auctionService.placeBid("A002", "Alice", 150.0);

        LocalDateTime newEndTime = auctionService.getAuction("A002").getEndTime();

        assertTrue(newEndTime.isAfter(originalEndTime),
                "endTime phải được gia hạn sau khi có bid trong cửa sổ cuối phiên");

        System.out.println("endTime cũ  : " + originalEndTime);
        System.out.println("endTime mới : " + newEndTime);
    }

    /**
     * Đặt giá khi vẫn còn dồi dào thời gian (nằm ngoài khung giờ chống bắn tỉa) → endTime không đổi.
     */
    @Test
    public void testAntiSnipe_NoExtensionWhenNotNearEnd() {
        LocalDateTime originalEndTime = auctionNormal.getEndTime();

        // Phiên đấu giá còn 1 tiếng — nằm ngoài khung giờ chống bắn tỉa 60 giây
        auctionService.placeBid("A001", "Alice", 150.0);

        assertEquals(originalEndTime, auctionService.getAuction("A001").getEndTime(),
                "endTime không được thay đổi khi bid còn cách xa thời điểm kết thúc");
    }

    // ════════════════════════════════════════════════════════════════
    // CÁC LỚP GIẢ (STUBS) — tránh việc phải kết nối tới DB thật trong các unit test
    // ════════════════════════════════════════════════════════════════

    /** Lớp giả cho AuctionDAO: mọi thao tác ghi đều coi như thành công (no-ops); getAllOpenAuctions trả về danh sách rỗng. */
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
     * Lớp giả cho UserDAO: getAvailableBalance() luôn trả về 999_999 để các bước kiểm tra số dư
     * bên trong AuctionService.placeBid() luôn vượt qua. Mọi phương thức khác đều không gây tác động (no-ops).
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