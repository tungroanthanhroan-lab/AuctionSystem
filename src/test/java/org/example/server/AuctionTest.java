package org.example.server;

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
    private AuctionNotifier dummyNotifier;

    @BeforeEach
    public void setUp() {
        // Khởi tạo người dùng đúng cấu trúc: id, username, password, budget (hoặc tham số tương ứng)
        alice = new Bidder("B01", "Alice", "pass", 5000.0);
        bob = new Bidder("B02", "Bob", "pass", 6000.0);
        dummyNotifier = new AuctionNotifier();

        // 1. Sửa lỗi khởi tạo Item: Truyền đầy đủ các tham số (id, tên, mô tả, giá khởi điểm,...)
        Item item = new Item("I1", "Iphone 15", "Brand new", 100.0, 100.0, "2026-12-31", 1, "OPEN");

        // 2. Sửa lỗi khởi tạo AuctionService: Truyền đúng tham số theo cấu trúc class của bạn
        // Cấu trúc dự kiến: id, item, leader, status, price, startTime, endTime, notifier
        auction = new AuctionService("A001", item, null, AuctionStatus.OPEN, 100.0,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), dummyNotifier);

        // Đảm bảo trạng thái là RUNNING trước khi đặt giá
        auction.setStatus(AuctionStatus.RUNNING);
    }

    // Test Case 1: Đặt giá hợp lệ
    @Test
    public void testPlaceBid_Success() {
        // 3. Sửa lỗi 'void cannot be dereferenced':
        // Nếu placeBid trả về void, chúng ta không gán vào biến BidResponse
        auction.placeBid(alice, 150.0);

        // Kiểm tra xem giá đã được cập nhật lên 150 và người dẫn đầu là Alice chưa
        assertEquals(150.0, auction.getCurrentHighestBid(), "Giá cao nhất phải là 150.0");
        assertEquals(alice, auction.getCurrentLeader(), "Người dẫn đầu phải là Alice");
    }

    // Test Case 2: Đặt giá thấp hơn giá hiện tại
    @Test // Đã chuyển từ TestNG sang JUnit 5 để đồng bộ
    public void testPlaceBid_Fail_LowerThanCurrentPrice() {
        // Alice đặt 150 trước
        auction.placeBid(alice, 150.0);

        // Bob đặt 120 (thấp hơn 150) -> Phải văng ra InvalidBidException
        Exception exception = assertThrows(InvalidBidException.class, () -> {
            auction.placeBid(bob, 120.0);
        });

        // Kiểm tra message lỗi (Sửa lại cho khớp với logic trong AuctionService của bạn)
        assertThrows(InvalidBidException.class, () -> {
            auction.placeBid(bob, 120.0);
        });

        // Giá vẫn phải là 150 của Alice
        assertEquals(150.0, auction.getCurrentHighestBid());
        assertEquals(alice, auction.getCurrentLeader());
    }

    // Test Case 3: Đặt giá khi phiên đã đóng
    @Test
    public void testPlaceBid_Fail_AuctionClosed() {
        // Chuyển sang trạng thái FINISHED
        auction.setStatus(AuctionStatus.FINISHED);

        // Đặt giá khi đã kết thúc -> Phải văng ra AuctionClosedException
        assertThrows(AuctionClosedException.class, () -> {
            auction.placeBid(alice, 200.0);
        });
    }
}
