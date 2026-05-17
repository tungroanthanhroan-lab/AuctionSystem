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
    private int version;

    private final Lock lock = new ReentrantLock(true);
    /**
     * Constructor đầy đủ dùng cho quá trình khởi tạo phiên mới hoặc Test.
     */
    public Auction(String auctionId, Item item, Bidder currentLeader,
                   AuctionStatus status, double startingPrice,
                   LocalDateTime startTime, LocalDateTime endTime,
                   AuctionNotifier notifier) {
        this.auctionId        = auctionId;
        this.item             = item;
        this.currentLeader    = currentLeader;
        this.status           = (status != null) ? status : AuctionStatus.OPEN;
        this.currentHighestBid = startingPrice;
        this.startTime        = startTime;
        this.endTime          = endTime;
        this.bidHistory       = new ArrayList<>();
        this.notifier         = notifier;
        this.version          = 0;
    }

    // Constructor dùng trong AuctionDAO.getAllOpenAuctions()
    public Auction(int id, int itemId, String startTime, String endTime, String status) {
        this.auctionId        = String.valueOf(id);
        this.status           = AuctionStatus.valueOf(status);
        this.currentHighestBid = 0;
        this.bidHistory       = new ArrayList<>();
        this.version          = 0;
    }
    /**
     * Phương thức quan trọng nhất: Xử lý đặt giá (Place Bid).
     * Trả về BidResponse để phía Controller hoặc Test case có thể assert kết quả.
     */
    public BidResponse placeBid(Bidder bidder, double bidAmount) {
        // Bắt đầu khóa để đảm bảo chỉ 1 thread được xử lý một lượt bid tại một thời điểm
        lock.lock();
        try {
            // 1. Tự động kích hoạt phiên đấu giá nếu có người bắt đầu đặt giá
            if (status == AuctionStatus.OPEN) {
                status = AuctionStatus.RUNNING;
            }
            // 2. Kiểm tra trạng thái hợp lệ của phiên
            if (status != AuctionStatus.RUNNING) {
                throw new AuctionClosedException("Phien dau gia nay hien khong mo! Vui long quay lai sau!");
            }
            // 3. Kiểm tra thời gian kết thúc (Self-check)
            if (LocalDateTime.now().isAfter(endTime)) {
                closeAuction();
                throw new AuctionClosedException("Phien dau gia hien da dong!");
            }
            // 4. Kiểm tra quy tắc giá: Giá mới phải lớn hơn giá hiện tại
            if (bidAmount <= currentHighestBid) {
                throw new InvalidBidException("Muc gia dat phai cao hơn muc gia hien tai: " + currentHighestBid);
            }
            // 5. Cập nhật trạng thái mới cho phiên đấu giá
            currentHighestBid = bidAmount;
            currentLeader     = bidder;
            bidHistory.add(new BidTransaction(bidder, bidAmount, LocalDateTime.now()));

            System.out.println("[BID] " + bidder.getUsername() + " dat gia " + bidAmount);
            // 6. Thông báo sự kiện (Broadcast) tới tất cả các Client đang theo dõi
            if (notifier != null) {
                BidUpdateEvent event = new BidUpdateEvent(
                        auctionId, bidAmount, bidder, LocalDateTime.now().toString()
                );
                notifier.broadcast(event);
            }

            return new BidResponse(true, "Dat gia thanh cong!", currentHighestBid);

        } finally {
            lock.unlock();
        }
    }
    /**
     * Kết thúc phiên đấu giá và chốt người thắng cuộc.
     */
    public synchronized void closeAuction() {
        if (status == AuctionStatus.RUNNING || status == AuctionStatus.OPEN) {
            status = AuctionStatus.FINISHED;
            if (currentLeader != null) {
                System.out.println("Phien dau gia ket thuc, nguoi thang cuoc la " + currentLeader.getUsername()
                        + " voi muc gia " + currentHighestBid);
            } else {
                System.out.println("Phien dau gia ket thuc, khong co nguoi dat gia");
            }
        }
    }
 // Getter và Setter
    public String getAuctionId() { return auctionId; }
    public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public Bidder getCurrentLeader() { return currentLeader; }
    public void setCurrentLeader(Bidder currentLeader) { this.currentLeader = currentLeader; }

    public double getCurrentHighestBid() { return currentHighestBid; }
    public void setCurrentHighestBid(double v) { this.currentHighestBid = v; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime t) { this.startTime = t; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime t) { this.endTime = t; }

    public List<BidTransaction> getBidHistory() { return bidHistory; }
    public void setBidHistory(List<BidTransaction> h) { this.bidHistory = h; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
