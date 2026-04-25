package service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

interface Item {}// se xoa sau khi clone code**
interface Bidder {}

interface currentLeader{
}
// phien dau gia con mo khong
// gia co cao hon hien tai khong (logic)
// hop le -> tiep tuc
public class AuctionService {
    private String auctionid;
    private Item item;
    private Bidder currentLeader;
    private AuctionStatus status;
    private double currentHighestBid;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<BidTransaction> bidHistory;

    public AuctionService(String auctionid, Item item, Bidder currentLeader, AuctionStatus status, double startingPrice, LocalDateTime startTime, LocalDateTime endTime) {
        this.auctionid = auctionid;
        this.item = item;
        this.currentLeader = currentLeader;
        this.status = AuctionStatus.OPEN;
        this.currentHighestBid = startingPrice;
        this.startTime = startTime;
        this.endTime = endTime;
        this.bidHistory = new ArrayList<>();
    }

    public String getAuctionid() {
        return auctionid;
    }

    public void setAuctionid(String auctionid) {
        this.auctionid = auctionid;
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

    /**
     * xu li logic dat gia cua nguoi dung
     * guard clause bat loi
     */
    public synchronized void placeBid(Bidder bidder, double bidAmount) {
        //kiem tra trang thai phien dau gia
        if (this.status != AuctionStatus.RUNNING) {
            throw new IllegalStateException("Phien dau gia nay hien khong mo! Vui long quay lai sau!");
        }

        //kiem tra dat gia phai cao hon gia hien tai
        if (bidAmount <= this.currentHighestBid) {
            throw new IllegalArgumentException("Muc gia dat phai cao hon muc gia hien tai");
        }

        //kiem tra thoi gian de phong loi luong chua kip dong phien
        if (LocalDateTime.now().isAfter(endTime)) {
            closeAuction();
            throw new IllegalStateException("Phien dau gia hien da dong!");
        }
        //cap nhat nguoi dan dau va muc gia
        this.currentHighestBid = bidAmount;
        this.currentLeader = bidder;

        BidTransaction transaction = new BidTransaction(bidder, bidAmount, LocalDateTime.now());
        this.bidHistory.add(transaction);

        // TODO: (Nâng cao) Kích hoạt Event/Observer để thông báo realtime cho các client khác

    }

    /**
     * logic dong phien dau gia
     */
    public synchronized void closeAuction() {
        if (this.status == AuctionStatus.RUNNING) {
            this.status = AuctionStatus.FINISHED; //chuyen trang thai sang hoan thanh

            //xac dinh nguoi thang cuoc
            if (this.currentLeader != null) {
                System.out.println("Phien dau gia ket thuc, nguoi thang cuoc la " + this.currentLeader);
            } else {
                System.out.println("Phien dau gia ket thuc, khong co nguoi dat gia");
            }
        }
    }
}


