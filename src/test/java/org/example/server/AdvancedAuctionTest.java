package org.example.server;

import org.example.exception.AuctionClosedException;
import org.example.exception.InvalidBidException;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test tính năng Auto-Bidding và Anti-Sniping.
 * Đặt file này vào: src/test/java/org/example/server/AdvancedAuctionTest.java
 */
public class AdvancedAuctionTest {

    private AuctionService auction;
    private AuctionService auctionNearEnd; // Phiên sắp hết giờ (dùng cho anti-snipe)
    private Bidder alice;
    private Bidder bob;
    private Bidder charlie;
    private AuctionNotifier dummyNotifier;

    @BeforeEach
    public void setUp() {
        alice   = new Bidder(1, "Alice",   "pass", 5000.0);
        bob     = new Bidder(2, "Bob",     "pass", 6000.0);
        charlie = new Bidder(3, "Charlie", "pass", 7000.0);
        dummyNotifier = new AuctionNotifier();

        // Đã sửa String id ("I1") thành int id (1)
        Item item = new Item(1, "Iphone 15", "Brand new", 100.0, 100.0,
                "2026-12-31", 1, "OPEN");

        // Phiên thông thường)
        auction = new AuctionService("A001", item, null, AuctionStatus.OPEN, 100.0,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), dummyNotifier);
        auction.setStatus(AuctionStatus.RUNNING);

        // Phiên sắp hết giờ: Đã sửa String id ("A002") thành int id (2)
        auctionNearEnd = new AuctionService("A002", item, null, AuctionStatus.OPEN, 100.0,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusSeconds(30), dummyNotifier);
        auctionNearEnd.setStatus(AuctionStatus.RUNNING);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  AUTO-BIDDING TESTS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Khi Alice đặt tay thủ công, auto-bid của Bob phải tự động phản hồi.
     * Bob có maxBid=300, increment=20 → Bob sẽ tự tăng lên 170.
     */
    @Test
    public void testAutoBid_TriggeredAfterManualBid() throws InterruptedException {
        // Bob đăng ký auto-bid: tối đa 300, mỗi lần tăng 20
        AutoBidConfig bobConfig = new AutoBidConfig(bob, 300.0, 20.0);
        auction.registerAutoBid(bobConfig);

        // Alice đặt giá 150 thủ công
        auction.placeBid(alice, 150.0);

        // Cho luồng auto-bid thời gian xử lý
        Thread.sleep(500);

        // Bob phải tự động vượt Alice: 150 + 20 = 170
        assertEquals(170.0, auction.getCurrentHighestBid(), 0.001,
                "Bob phai tu dong dat gia 170");
        assertEquals(bob, auction.getCurrentLeader(),
                "Bob phai la nguoi dan dau sau auto-bid");
    }

    /**
     * Auto-bid không được vượt quá maxBid đã đặt.
     * Bob có maxBid=160, Alice đặt 150 → Bob chỉ đủ tăng lên 170 nhưng 170 > 160 → KHÔNG bid.
     */
    @Test
    public void testAutoBid_DoesNotExceedMaxBid() throws InterruptedException {
        // Bob maxBid=160, increment=20 → next = 150+20=170 > 160 → bị chặn
        AutoBidConfig bobConfig = new AutoBidConfig(bob, 160.0, 20.0);
        auction.registerAutoBid(bobConfig);

        auction.placeBid(alice, 150.0);
        Thread.sleep(300);

        // Bob không thể bid, Alice vẫn dẫn đầu
        assertEquals(150.0, auction.getCurrentHighestBid(), 0.001,
                "Gia phai giu nguyen vi Bob khong du maxBid");
        assertEquals(alice, auction.getCurrentLeader(),
                "Alice van phai la nguoi dan dau");
    }

    /**
     * Khi 2 người cùng có auto-bid và cùng maxBid, người đăng ký SỚM HƠN thắng cuối cùng.
     *
     * Logic thực tế:
     * Alice bid 150 → Bob (đăng ký trước) auto-bid 170 → Charlie counter 190
     * → Bob 210 → Charlie 230 → ... → Bob và Charlie leo thang đến gần maxBid.
     * Khi đến đỉnh (cả hai không còn increment nào khớp), người dẫn đầu cuối cùng
     * là người được ưu tiên (Bob) vì Bob được xét trước trong mỗi vòng.
     *
     * Với maxBid=500, increment=20, giá bắt đầu 150:
     * Dãy: 170(Bob) 190(C) 210(B) 230(C) ... 490(B) 510(C - vượt maxBid → dừng)
     * → Bob dừng ở 490, Charlie không thể vượt vì 490+20=510 > 500 → Bob thắng.
     */
    @Test
    public void testAutoBid_PriorityByRegistrationTime() throws InterruptedException {
        // Bob đăng ký trước, Charlie đăng ký sau — cùng maxBid và increment
        AutoBidConfig bobConfig     = new AutoBidConfig(bob,     500.0, 20.0);
        Thread.sleep(20); // Đảm bảo timestamp khác nhau rõ ràng
        AutoBidConfig charlieConfig = new AutoBidConfig(charlie, 500.0, 20.0);

        auction.registerAutoBid(bobConfig);
        auction.registerAutoBid(charlieConfig);

        auction.placeBid(alice, 150.0); // Kích hoạt chuỗi auto-bid

        // Cho đủ thời gian để chuỗi leo thang hoàn thành
        Thread.sleep(2000);

        // Bob đăng ký trước → trong mỗi vòng Bob được xét trước →
        // Bob luôn là người đặt giá cuối trong chuỗi leo thang → Bob thắng
        assertEquals(bob, auction.getCurrentLeader(),
                "Bob dang ky truoc nen thang cuoi cung khi ca hai co cung maxBid");

        // Xác nhận giá đã leo thang (không còn dừng ở 170)
        assertTrue(auction.getCurrentHighestBid() > 170.0,
                "Gia phai duoc leo thang qua nhieu vong auto-bid");

        System.out.println("Gia cuoi: " + auction.getCurrentHighestBid()
                + " | Nguoi thang: " + auction.getCurrentLeader().getUsername());
    }

    /**
     * Hủy đăng ký auto-bid: sau khi hủy, Bob không còn tự động bid nữa.
     */
    @Test
    public void testCancelAutoBid() throws InterruptedException {
        AutoBidConfig bobConfig = new AutoBidConfig(bob, 300.0, 20.0);
        auction.registerAutoBid(bobConfig);

        // Hủy auto-bid của Bob
        auction.cancelAutoBid(bob);
        assertEquals(0, auction.getAutoBidCount(), "Phai khong con auto-bid nao");

        // Alice đặt giá
        auction.placeBid(alice, 150.0);
        Thread.sleep(300);

        // Bob không tự động bid → Alice vẫn dẫn đầu
        assertEquals(alice, auction.getCurrentLeader(),
                "Alice van dan dau vi Bob da huy auto-bid");
    }

    /**
     * Đăng ký auto-bid với maxBid thấp hơn giá hiện tại → phải ném InvalidBidException.
     */
    @Test
    public void testRegisterAutoBid_Fail_MaxBidTooLow() {
        auction.placeBid(alice, 200.0); // Giá hiện tại = 200

        // Bob đăng ký maxBid=150 < 200 → lỗi
        AutoBidConfig badConfig = new AutoBidConfig(bob, 150.0, 10.0);
        assertThrows(InvalidBidException.class, () -> auction.registerAutoBid(badConfig),
                "Phai nem InvalidBidException khi maxBid < currentHighestBid");
    }

    /**
     * Đăng ký auto-bid khi phiên đã đóng → phải ném AuctionClosedException.
     */
    @Test
    public void testRegisterAutoBid_Fail_AuctionClosed() {
        auction.setStatus(AuctionStatus.FINISHED);

        AutoBidConfig config = new AutoBidConfig(bob, 300.0, 20.0);
        assertThrows(AuctionClosedException.class, () -> auction.registerAutoBid(config),
                "Phai nem AuctionClosedException khi phien da dong");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ANTI-SNIPING TESTS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Bid trong cửa sổ cuối → thời gian phải được gia hạn.
     */
    @Test
    public void testAntiSnipe_ExtendsEndTime() {
        LocalDateTime originalEndTime = auctionNearEnd.getEndTime();

        // Alice đặt giá khi chỉ còn 30 giây (nằm trong cửa sổ 60 giây)
        auctionNearEnd.placeBid(alice, 150.0);

        LocalDateTime newEndTime = auctionNearEnd.getEndTime();

        // endTime phải được đẩy về sau
        assertTrue(newEndTime.isAfter(originalEndTime),
                "endTime phai duoc gia han sau khi co bid cuoi phien");

        System.out.println("endTime cu  : " + originalEndTime);
        System.out.println("endTime moi : " + newEndTime);
    }

    /**
     * Bid khi còn nhiều thời gian (ngoài cửa sổ) → endTime KHÔNG thay đổi.
     */
    @Test
    public void testAntiSnipe_NoExtensionWhenNotNearEnd() {
        LocalDateTime originalEndTime = auction.getEndTime();

        // Phiên còn 1 tiếng → ngoài cửa sổ 60 giây
        auction.placeBid(alice, 150.0);

        assertEquals(originalEndTime, auction.getEndTime(),
                "endTime khong duoc thay doi khi bid con cach xa thoi diem ket thuc");
    }
}