package org.example.service;

import org.example.model.Item;
import org.example.model.Bidder;
import org.example.exception.AuctionClosedException;
import org.example.exception.InvalidBidException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// phien dau gia con mo khong
// gia co cao hon hien tai khong (logic)
// hop le -> tiep tuc
public class AuctionService {
    private String auctionId;
    private Item item;
    private Bidder currentLeader;
    private AuctionStatus status;
    private double currentHighestBid;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<BidTransaction> bidHistory;
    private AuctionNotifier notifier;

    public AuctionService(String auctionId, Item item, Bidder currentLeader, AuctionStatus status, double startingPrice, LocalDateTime startTime, LocalDateTime endTime, AuctionNotifier notifier) {
        this.auctionId = auctionId;
        this.item = item;
        this.currentLeader = currentLeader;
        this.status = AuctionStatus.OPEN;
        this.currentHighestBid = startingPrice;
        this.startTime = startTime;
        this.endTime = endTime;
        this.bidHistory = new ArrayList<>();
        this.notifier = notifier;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Bidder getCurrentLeader() {
        return currentLeader;
    }

    public void setCurrentLeader(Bidder currentLeader) {
        this.currentLeader = currentLeader;
    }

    public double getCurrentHighestBid() {
        return currentHighestBid;
    }

    public void setCurrentHighestBid(double currentHighestBid) {
        this.currentHighestBid = currentHighestBid;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public List<BidTransaction> getBidHistory() {
        return bidHistory;
    }

    public void setBidHistory(List<BidTransaction> bidHistory) {
        this.bidHistory = bidHistory;
    }

    //dung reentranlock thay vi synchroized
    private final Lock lock = new ReentrantLock(true);

    /**
     * xu li logic dat gia cua nguoi dung
     * guard clause bat loi
     */
    public void placeBid(Bidder bidder, double bidAmount) {
        lock.lock();
        try {
            //kiem tra trang thai phien dau gia
            if (status != AuctionStatus.RUNNING) {
                throw new AuctionClosedException("Phien dau gia nay hien khong mo! Vui long quay lai sau!");
            }

            //kiem tra dat gia phai cao hon gia hien tai
            if (bidAmount <= currentHighestBid) {
                throw new InvalidBidException("Muc gia dat phai cao hon muc gia hien tai");
            }

            //kiem tra thoi gian de phong loi luong chua kip dong phien
            if (LocalDateTime.now().isAfter(endTime)) {
                closeAuction();
                throw new AuctionClosedException("Phien dau gia hien da dong!");
            }
            //cap nhat nguoi dan dau va muc gia
            currentHighestBid = bidAmount;
            currentLeader = bidder;

            BidTransaction transaction = new BidTransaction(bidder, bidAmount, LocalDateTime.now());
            bidHistory.add(transaction);
        } finally {
            lock.unlock();
        }


        // TODO: (Nâng cao) Kích hoạt Event/Observer để thông báo realtime cho các client khác

    }

    /**
     * logic dong phien dau gia
     */
    public synchronized void closeAuction() {
        if (status == AuctionStatus.RUNNING) {
            status = AuctionStatus.FINISHED; //chuyen trang thai sang hoan thanh

            //xac dinh nguoi thang cuoc
            if (currentLeader != null) {
                System.out.println("Phien dau gia ket thuc, nguoi thang cuoc la " + currentLeader);
            } else {
                System.out.println("Phien dau gia ket thuc, khong co nguoi dat gia");
            }
        }
    }
}


