package org.example.server;

import org.example.controller.BidResponse;
import org.example.exception.InvalidBidException;
import org.example.exception.AuctionClosedException;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.service.AuctionNotifier;
import org.example.service.AuctionService;
import org.example.service.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

public class AuctionTest {

    private AuctionService auction;
    private Bidder alice;
    private Bidder bob;
    private AuctionNotifier dummyNotifier; // Dùng một notifier giả để test logic

    // Hàm @BeforeEach chạy trước MỖI hàm @Test để reset lại dữ liệu mới tinh
    @BeforeEach
    public void setUp() {
        alice = new Bidder("B01", "Alice", null, 5000);
        bob = new Bidder("B02", "Bob", null, 6000);
        dummyNotifier = new AuctionNotifier();

        Item item = new Item("Iphone 15");
        // Giả sử tạo phiên đấu giá giá khởi điểm 100$, thời gian kết thúc là 1 giờ sau
        auction = new AuctionService("A001", item, 100.0, LocalDateTime.now(), LocalDateTime.now().plusHours(1), dummyNotifier);

        // Gán trạng thái là RUNNING để test
        auction.setStatus(AuctionStatus.RUNNING);
    }

    // Test Case 1: Đặt giá hợp lệ
    @Test
    public void testPlaceBid_Success() {
        // Alice đặt giá 150
        BidResponse response = auction.placeBid(alice, 150.0);

        // Khẳng định (Assert) kết quả phải thành công, giá hiện tại phải là 150, người dẫn đầu là Alice
        assertTrue(response.isSuccess());
        assertEquals(150.0, auction.getCurrentHighestBid());
        assertEquals(alice, auction.getCurrentLeader());
    }

    // Test Case 2: Đặt giá thấp hơn giá hiện tại (Phải quăng Exception)
    @org.testng.annotations.Test
    public void testPlaceBid_Fail_LowerThanCurrentPrice() {
        // Alice đặt 150 trước
        auction.placeBid(alice, 150.0);

        // Bob đặt 120 (thấp hơn 150) -> Bắt buộc phải văng ra InvalidBidException
        Exception exception = assertThrows(InvalidBidException.class, () -> {
            auction.placeBid(bob, 120.0);
        });

        // Kiểm tra xem lời nhắn lỗi có đúng như mong muốn không
        assertTrue(exception.getMessage().contains("phải cao hơn"));

        // Khẳng định giá hiện tại vẫn phải là 150 của Alice, không bị đè thành 120
        assertEquals(150.0, auction.getCurrentHighestBid());
        assertEquals(alice, auction.getCurrentLeader());
    }

    // Test Case 3: Đặt giá khi phiên đã đóng
    @Test
    public void testPlaceBid_Fail_AuctionClosed() {
        // Cố tình chuyển trạng thái phiên đấu giá sang FINISHED
        auction.setStatus(AuctionStatus.FINISHED);

        Exception exception = assertThrows(AuctionClosedException.class, () -> {
            auction.placeBid(alice, 200.0);
        });

        assertEquals("Thất bại: Phiên đấu giá này đã kết thúc hoặc chưa mở.", exception.getMessage());
    }
}