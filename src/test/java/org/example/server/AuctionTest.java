package org.example.server;

import org.example.controller.BidResponse;
import org.example.exception.AuctionClosedException;
import org.example.exception.InvalidBidException;
import org.example.model.Auction;
import org.example.model.AuctionStatus;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.observer.AuctionNotifier;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test kiểm tra toàn bộ logic đấu giá trong Auction.
 *
 * Các nhóm test:
 *  1. Đặt giá hợp lệ
 *  2. Đặt giá thất bại (giá thấp, phiên đóng)
 *  3. Chuyển trạng thái OPEN → RUNNING
 *  4. Đóng phiên
 *  5. Concurrency — nhiều thread đặt giá đồng thời
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class AuctionTest {

    private Auction auction;
    private Bidder alice;
    private Bidder bob;
    private AuctionNotifier notifier;

    /**
     * Khởi tạo lại toàn bộ dữ liệu trước mỗi test.
     * Phiên bắt đầu ở trạng thái OPEN, giá khởi điểm 100.0, kết thúc sau 1 giờ.
     */
    @BeforeEach
    public void setUp() {
        // FIX: Bidder dùng constructor đầy đủ (id, username, password, role, balance)
        alice = new Bidder(1, "Alice", "pass", "BIDDER", 1000.0);
        bob   = new Bidder(2, "Bob",   "pass", "BIDDER", 800.0);

        notifier = new AuctionNotifier();

        // FIX: Item dùng constructor đầy đủ (id, title, description, startingPrice, currentPrice, endTime, sellerId, status)
        Item item = new Item(1, "iPhone 15 Pro", "Điện thoại cao cấp",
                100.0, 100.0, LocalDateTime.now().plusHours(1).toString(), 99, "OPEN");

        // FIX: Auction khởi tạo với status=OPEN — hợp lệ để đặt giá
        auction = new Auction("A001", item, null, AuctionStatus.OPEN,
                100.0,
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1),
                notifier);
    }

    @AfterEach
    public void tearDown() {
        notifier.shutdown();
    }

    // ════════════════════════════════════════════════════════════════
    // Nhóm 1: Đặt giá HỢP LỆ
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1.1 Đặt giá hợp lệ — thành công, cập nhật đúng giá và người dẫn đầu")
    public void testPlaceBid_Success() {
        BidResponse response = auction.placeBid(alice, 150.0);

        assertTrue(response.isSuccess(), "Response phải là success");
        assertEquals(150.0, auction.getCurrentHighestBid(), "Giá phải cập nhật thành 150");
        assertEquals(alice, auction.getCurrentLeader(), "Người dẫn đầu phải là Alice");
    }

    @Test
    @DisplayName("1.2 Alice bid 150, Bob bid 200 — Bob thắng, giá là 200")
    public void testPlaceBid_TwoRounds_SecondBidderWins() {
        auction.placeBid(alice, 150.0);
        BidResponse response = auction.placeBid(bob, 200.0);

        assertTrue(response.isSuccess());
        assertEquals(200.0, auction.getCurrentHighestBid());
        assertEquals(bob, auction.getCurrentLeader());
    }

    @Test
    @DisplayName("1.3 Đặt giá đúng bằng khởi điểm + 0.01 — hợp lệ")
    public void testPlaceBid_JustAboveStartingPrice() {
        BidResponse response = auction.placeBid(alice, 100.01);
        assertTrue(response.isSuccess());
        assertEquals(100.01, auction.getCurrentHighestBid(), 0.001);
    }

    @Test
    @DisplayName("1.4 Lịch sử bid được ghi lại đúng số lượt")
    public void testBidHistory_RecordedCorrectly() {
        auction.placeBid(alice, 150.0);
        auction.placeBid(bob,   200.0);
        auction.placeBid(alice, 250.0);

        assertEquals(3, auction.getBidHistory().size(), "Phải có đúng 3 lượt bid trong lịch sử");
    }

    // ════════════════════════════════════════════════════════════════
    // Nhóm 2: Đặt giá THẤT BẠI
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("2.1 Giá đặt bằng giá hiện tại — InvalidBidException")
    public void testPlaceBid_Fail_EqualToCurrentPrice() {
        auction.placeBid(alice, 150.0);

        assertThrows(InvalidBidException.class, () ->
                        auction.placeBid(bob, 150.0),
                "Giá bằng giá hiện tại phải ném InvalidBidException"
        );
        // Giá và người dẫn đầu không được thay đổi
        assertEquals(150.0, auction.getCurrentHighestBid());
        assertEquals(alice, auction.getCurrentLeader());
    }

    @Test
    @DisplayName("2.2 Giá đặt thấp hơn giá hiện tại — InvalidBidException, message chứa 'cao hơn'")
    public void testPlaceBid_Fail_LowerThanCurrentPrice() {
        auction.placeBid(alice, 150.0);

        Exception ex = assertThrows(InvalidBidException.class, () ->
                auction.placeBid(bob, 120.0)
        );

        assertTrue(ex.getMessage().contains("cao hơn"),
                "Thông báo lỗi phải đề cập 'cao hơn'");
        assertEquals(150.0, auction.getCurrentHighestBid());
        assertEquals(alice, auction.getCurrentLeader());
    }

    @Test
    @DisplayName("2.3 Đặt giá khi phiên FINISHED — AuctionClosedException")
    public void testPlaceBid_Fail_AuctionFinished() {
        auction.setStatus(AuctionStatus.FINISHED);

        assertThrows(AuctionClosedException.class, () ->
                auction.placeBid(alice, 200.0)
        );
    }

    @Test
    @DisplayName("2.4 Đặt giá khi phiên CANCELED — AuctionClosedException")
    public void testPlaceBid_Fail_AuctionCanceled() {
        auction.setStatus(AuctionStatus.CANCELED);

        assertThrows(AuctionClosedException.class, () ->
                auction.placeBid(alice, 200.0)
        );
    }

    @Test
    @DisplayName("2.5 Đặt giá khi phiên đã hết thời gian — AuctionClosedException, tự đóng phiên")
    public void testPlaceBid_Fail_Expired() {
        // Tạo phiên đã hết hạn (endTime trong quá khứ)
        Auction expiredAuction = new Auction("A002", null, null, AuctionStatus.OPEN,
                100.0,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusSeconds(1), // đã hết hạn
                notifier);

        assertThrows(AuctionClosedException.class, () ->
                expiredAuction.placeBid(alice, 150.0)
        );
        assertEquals(AuctionStatus.FINISHED, expiredAuction.getStatus(),
                "Phiên hết hạn phải tự chuyển sang FINISHED");
    }

    // ════════════════════════════════════════════════════════════════
    // Nhóm 3: Chuyển trạng thái
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("3.1 Phiên OPEN → tự chuyển RUNNING khi có giá đầu tiên")
    public void testStatus_OpenToRunning_OnFirstBid() {
        assertEquals(AuctionStatus.OPEN, auction.getStatus(), "Trạng thái ban đầu phải là OPEN");

        auction.placeBid(alice, 150.0);

        assertEquals(AuctionStatus.RUNNING, auction.getStatus(),
                "Sau bid đầu tiên phải chuyển sang RUNNING");
    }

    @Test
    @DisplayName("3.2 Phiên RUNNING vẫn chấp nhận bid tiếp theo")
    public void testStatus_RunningAcceptsBid() {
        auction.placeBid(alice, 150.0); // OPEN → RUNNING
        BidResponse response = auction.placeBid(bob, 200.0);

        assertTrue(response.isSuccess());
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }

    // ════════════════════════════════════════════════════════════════
    // Nhóm 4: Đóng phiên
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("4.1 closeAuction() chuyển trạng thái sang FINISHED")
    public void testCloseAuction_StatusBecomesFinished() {
        auction.placeBid(alice, 150.0);
        auction.closeAuction();

        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    }

    @Test
    @DisplayName("4.2 closeAuction() idempotent — gọi 2 lần không gây lỗi")
    public void testCloseAuction_Idempotent() {
        auction.placeBid(alice, 150.0);
        auction.closeAuction();
        assertDoesNotThrow(() -> auction.closeAuction(),
                "Gọi closeAuction() lần 2 không được ném exception");
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    }

    @Test
    @DisplayName("4.3 Đặt giá sau khi đóng phiên — AuctionClosedException")
    public void testPlaceBid_AfterClose_Fails() {
        auction.placeBid(alice, 150.0);
        auction.closeAuction();

        assertThrows(AuctionClosedException.class, () ->
                auction.placeBid(bob, 200.0)
        );
    }

    // ════════════════════════════════════════════════════════════════
    // Nhóm 5: Concurrency — nhiều thread đặt giá đồng thời
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("5.1 10 thread đặt giá đồng thời — chỉ 1 người thắng, giá nhất quán")
    @Execution(ExecutionMode.SAME_THREAD)
    public void testConcurrency_OnlyOneLeaderWins() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Mỗi thread cố đặt giá khác nhau (110, 120, ..., 200)
        for (int i = 1; i <= threadCount; i++) {
            final double bidAmount = 100 + (i * 10.0);
            final Bidder bidder = new Bidder(i, "Player" + i, "pass", "BIDDER", 500.0);
            futures.add(executor.submit(() -> {
                try {
                    auction.placeBid(bidder, bidAmount);
                    return true;
                } catch (Exception e) {
                    return false; // Giá thấp hơn/bằng giá hiện tại — bình thường
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Kết quả cuối cùng phải nhất quán
        double finalPrice = auction.getCurrentHighestBid();
        Bidder finalLeader = auction.getCurrentLeader();

        // Giá cuối phải cao hơn giá khởi điểm
        assertTrue(finalPrice > 100.0, "Giá cuối phải cao hơn 100 sau 10 lượt bid");

        // Người dẫn đầu không được là null
        assertNotNull(finalLeader, "Phải có người dẫn đầu sau khi bid đồng thời");

        // Số lượt trong lịch sử phải hợp lệ (0 < x <= threadCount)
        int historySize = auction.getBidHistory().size();
        assertTrue(historySize > 0 && historySize <= threadCount,
                "Lịch sử bid phải từ 1 đến " + threadCount + " lượt, thực tế: " + historySize);

        System.out.println("[Concurrency Test] Giá cuối: " + finalPrice
                + " | Người thắng: " + finalLeader.getUsername()
                + " | Số lượt ghi: " + historySize);
    }

    @Test
    @DisplayName("5.2 Thread A đặt giá, Thread B đóng phiên — không deadlock")
    public void testConcurrency_BidAndClose_NoDeadlock() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        Thread bidThread = new Thread(() -> {
            try {
                Thread.sleep(50); // Nhường B khởi động trước
                auction.placeBid(alice, 150.0);
            } catch (Exception ignored) {
                // Có thể bị AuctionClosedException nếu B đóng trước — OK
            } finally {
                latch.countDown();
            }
        });

        Thread closeThread = new Thread(() -> {
            try {
                Thread.sleep(30);
                auction.closeAuction();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });

        bidThread.start();
        closeThread.start();

        // Nếu sau 3 giây vẫn chưa xong → deadlock → fail
        boolean finished = latch.await(3, TimeUnit.SECONDS);
        assertTrue(finished, "Phải hoàn thành trong 3 giây — không deadlock");

        // Trạng thái cuối phải là FINISHED hoặc RUNNING (tùy thread nào thắng)
        AuctionStatus finalStatus = auction.getStatus();
        assertTrue(finalStatus == AuctionStatus.FINISHED
                        || finalStatus == AuctionStatus.RUNNING
                        || finalStatus == AuctionStatus.OPEN,
                "Trạng thái cuối phải hợp lệ: " + finalStatus);
    }
}