package org.example.model;

import org.example.controller.BidResponse;
import org.example.exception.AuctionClosedException;
import org.example.exception.InvalidBidException;
import org.example.observer.AuctionNotifier;
import org.example.observer.BidUpdateEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Model đấu giá — dùng trong AuctionTest.
 * placeBid() trả BidResponse để test có thể assert kết quả.
 * Trạng thái OPEN → tự chuyển RUNNING khi có bid đầu tiên.
 */
public class Auction {

    private String auctionId;
    private Item item;
    private Bidder currentLeader;
    private AuctionStatus status;
    private double currentHighestBid;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<BidTransaction> bidHistory;
    private AuctionNotifier notifier;

    // FIX: Thêm version để hỗ trợ Optimistic Locking với DB
    private int version;

    // FIX: Dùng ReentrantLock cho cả placeBid() và closeAuction() — nhất quán, tránh race condition
    private final Lock lock = new ReentrantLock(true);

    // Constructor đầy đủ — dùng khi tạo phiên mới
    public Auction(String auctionId, Item item, Bidder currentLeader, AuctionStatus status,
                   double startingPrice, LocalDateTime startTime, LocalDateTime endTime,
                   AuctionNotifier notifier) {
        this.auctionId = auctionId;
        this.item = item;
        this.currentLeader = currentLeader;
        this.status = (status != null) ? status : AuctionStatus.OPEN;
        this.currentHighestBid = startingPrice;
        this.startTime = startTime;
        this.endTime = endTime;
        this.bidHistory = new ArrayList<>();
        this.notifier = notifier;
        this.version = 0;
    }

    // FIX BUG 1: Constructor tiện lợi — dùng trong ClientHandler khi chỉ có auctionId
    public Auction(String auctionId) {
        this.auctionId = auctionId;
        this.bidHistory = new ArrayList<>();
        this.status = AuctionStatus.OPEN;
        this.version = 0;
    }

    // FIX BUG 1: Constructor cho AuctionDAO.getActiveAuctions() — load từ DB
    public Auction(int id, int itemId, String startTime, String endTime, String status) {
        this.auctionId = String.valueOf(id);
        this.status = parseStatus(status);
        this.currentHighestBid = 0;
        this.bidHistory = new ArrayList<>();
        this.version = 0;
    }

    // Helper: parse chuỗi status từ DB sang enum
    private AuctionStatus parseStatus(String statusStr) {
        try {
            return AuctionStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return AuctionStatus.OPEN;
        }
    }

    /**
     * Logic đặt giá — dùng ReentrantLock để thread-safe.
     * Trả về BidResponse để phía Controller hoặc Test case có thể assert kết quả.
     */
    public BidResponse placeBid(Bidder bidder, double bidAmount) {
        lock.lock();
        try {
            // Kiểm tra trạng thái phiên
            if (status == AuctionStatus.FINISHED
                    || status == AuctionStatus.CANCELED
                    || status == AuctionStatus.PAID) {
                throw new AuctionClosedException("Phiên đấu giá này đã đóng!");
            }

            // Kiểm tra giá phải cao hơn giá hiện tại
            if (bidAmount <= currentHighestBid) {
                throw new InvalidBidException("Mức giá đặt phải cao hơn mức giá hiện tại: " + currentHighestBid);
            }

            // Kiểm tra thời gian — phòng lỗi luồng chưa kịp đóng phiên
            if (endTime != null && LocalDateTime.now().isAfter(endTime)) {
                closeAuction(); // Dùng chung ReentrantLock không gây deadlock
                throw new AuctionClosedException("Phiên đấu giá đã hết thời gian!");
            }

            // Kích hoạt trạng thái RUNNING khi có giá đầu tiên
            if (status == AuctionStatus.OPEN) {
                status = AuctionStatus.RUNNING;
            }

            // Cập nhật người dẫn đầu và mức giá
            currentHighestBid = bidAmount;
            currentLeader = bidder;

            BidTransaction transaction = new BidTransaction(bidder, bidAmount, LocalDateTime.now());
            bidHistory.add(transaction);

            System.out.println("[BID] " + bidder.getUsername() + " đặt giá " + bidAmount);

            // Thông báo sự kiện (Broadcast) tới tất cả các Client đang theo dõi (Từ nhánh advanced)
            if (notifier != null) {
                BidUpdateEvent event = new BidUpdateEvent(
                        auctionId, bidAmount, bidder, LocalDateTime.now().toString()
                );
                notifier.broadcast(event);
            }

            return new BidResponse(true, "Đặt giá thành công!", currentHighestBid);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Đóng phiên đấu giá — dùng cùng ReentrantLock (reentrant: gọi được từ nội bộ).
     * FIX BUG 9: Bỏ synchronized, dùng ReentrantLock nhất quán.
     */
    public void closeAuction() {
        lock.lock();
        try {
            if (status == AuctionStatus.RUNNING || status == AuctionStatus.OPEN) {
                status = AuctionStatus.FINISHED;
                if (currentLeader != null) {
                    System.out.println("Phiên đấu giá [" + auctionId + "] kết thúc. Người thắng: "
                            + currentLeader.getUsername() + " với giá: " + currentHighestBid);
                } else {
                    System.out.println("Phiên đấu giá [" + auctionId + "] kết thúc, không có người đặt giá.");
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // ===================== Getters & Setters =====================

    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public Bidder getCurrentLeader() { return currentLeader; }
    public void setCurrentLeader(Bidder currentLeader) { this.currentLeader = currentLeader; }

    public double getCurrentHighestBid() { return currentHighestBid; }
    public void setCurrentHighestBid(double currentHighestBid) { this.currentHighestBid = currentHighestBid; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public List<BidTransaction> getBidHistory() { return bidHistory; }
    public void setBidHistory(List<BidTransaction> bidHistory) { this.bidHistory = bidHistory; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}