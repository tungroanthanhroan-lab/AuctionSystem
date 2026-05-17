package org.example.service;

import org.example.exception.AuctionClosedException;
import org.example.exception.InvalidBidException;
import org.example.model.AuctionStatus;
import org.example.model.Auction;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.model.BidTransaction;
import org.example.observer.AuctionNotifier;
import org.example.observer.BidUpdateEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AuctionService — quản lý một phiên đấu giá in-memory.
 * Tính năng: đặt giá thủ công, Auto-Bidding, Anti-Sniping.
 *
 * FIX: Thêm placeBid(String auctionId, String bidderName, double amount)
 *      để ClientHandler gọi được.
 * FIX: Thêm getActiveAuctionsList() để ClientHandler.handleViewItems() gọi được.
 */
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
    private final Lock lock = new ReentrantLock(true);

    private final List<AutoBidConfig> autoBidConfigs = new ArrayList<>();

    private static final long SNIPE_WINDOW_SECONDS    = 60;
    private static final long SNIPE_EXTENSION_SECONDS = 120;

    public AuctionService(String auctionId, Item item, Bidder currentLeader,
                          AuctionStatus status, double startingPrice,
                          LocalDateTime startTime, LocalDateTime endTime,
                          AuctionNotifier notifier) {
        this.auctionId         = auctionId;
        this.item              = item;
        this.currentLeader     = currentLeader;
        this.status            = AuctionStatus.OPEN;
        this.currentHighestBid = startingPrice;
        this.startTime         = startTime;
        this.endTime           = endTime;
        this.bidHistory        = new ArrayList<>();
        this.notifier          = notifier;
    }

    // ── PLACE BID (Bidder object) — dùng trong test và AuctionController ─────
    public void placeBid(Bidder bidder, double bidAmount) {
        lock.lock();
        try {
            if (status != AuctionStatus.RUNNING) {
                throw new AuctionClosedException("Phien dau gia nay hien khong mo! Vui long quay lai sau!");
            }
            if (LocalDateTime.now().isAfter(endTime)) {
                closeAuction();
                throw new AuctionClosedException("Phien dau gia hien da dong!");
            }
            if (bidAmount <= currentHighestBid) {
                throw new InvalidBidException("Muc gia dat phai cao hon muc gia hien tai: " + currentHighestBid);
            }

            currentHighestBid = bidAmount;
            currentLeader     = bidder;
            bidHistory.add(new BidTransaction(bidder, bidAmount, LocalDateTime.now()));
            System.out.println("[BID] " + bidder.getUsername() + " dat gia " + bidAmount);

            applyAntiSnipe();

            if (notifier != null) {
                notifier.broadcast(new BidUpdateEvent(auctionId, bidAmount, bidder, LocalDateTime.now().toString()));
            }

            triggerAutoBids(bidder);

        } finally {
            lock.unlock();
        }
    }

    // ── PLACE BID (String) — dùng trong ClientHandler ────────────────────────
    // FIX: ClientHandler gọi auctionService.placeBid(auctionId, bidderName, amount)
    public boolean placeBid(String auctionId, String bidderName, double amount) {
        if (!this.auctionId.equals(auctionId)) return false;
        try {
            placeBid(new Bidder(bidderName), amount);
            return true;
        } catch (Exception e) {
            System.out.println("[AuctionService] placeBid failed: " + e.getMessage());
            return false;
        }
    }

    // ── GET ACTIVE AUCTIONS — dùng trong ClientHandler.handleViewItems() ─────
    // FIX: ClientHandler gọi auctionService.getActiveAuctionsList()
    public List<Auction> getActiveAuctionsList() {
        List<Auction> list = new ArrayList<>();
        if (status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING) {
            Auction a = new Auction(auctionId, item, currentLeader, status,
                    currentHighestBid, startTime, endTime, notifier);
            a.setCurrentHighestBid(currentHighestBid);
            a.setVersion(0);
            list.add(a);
        }
        return list;
    }

    // ── ANTI-SNIPING ──────────────────────────────────────────────────────────
    private void applyAntiSnipe() {
        LocalDateTime windowStart = endTime.minusSeconds(SNIPE_WINDOW_SECONDS);
        if (LocalDateTime.now().isAfter(windowStart)) {
            LocalDateTime newEnd = endTime.plusSeconds(SNIPE_EXTENSION_SECONDS);
            System.out.println("[ANTI-SNIPE] Gia han phien den: " + newEnd);
            endTime = newEnd;
        }
    }

    // ── AUTO-BIDDING ──────────────────────────────────────────────────────────
    public void registerAutoBid(AutoBidConfig config) {
        lock.lock();
        try {
            if (status != AuctionStatus.RUNNING) {
                throw new AuctionClosedException("Khong the dang ky auto-bid khi phien da dong!");
            }
            if (config.getMaxBid() <= currentHighestBid) {
                throw new InvalidBidException(
                        "maxBid (" + config.getMaxBid() + ") phai lon hon gia hien tai (" + currentHighestBid + ")");
            }
            cancelAutoBid(config.getBidder());
            autoBidConfigs.add(config);
            System.out.println("[AUTO-BID] Da dang ky: " + config);
        } finally {
            lock.unlock();
        }
    }

    public void cancelAutoBid(Bidder bidder) {
        lock.lock();
        try {
            autoBidConfigs.removeIf(c -> c.getBidder().getId() == bidder.getId());
            System.out.println("[AUTO-BID] Da huy auto-bid cua: " + bidder.getUsername());
        } finally {
            lock.unlock();
        }
    }

    private void triggerAutoBids(Bidder lastBidder) {
        new Thread(() -> processAutoBids(lastBidder)).start();
    }

    private void processAutoBids(Bidder skipBidder) {
        boolean anyBidPlaced = true;
        while (anyBidPlaced) {
            anyBidPlaced = false;
            lock.lock();
            try {
                if (status != AuctionStatus.RUNNING) break;
                AutoBidConfig winner = null;
                for (AutoBidConfig cfg : autoBidConfigs) {
                    if (cfg.getBidder().getId() == currentLeader.getId()) continue;
                    double nextBid = currentHighestBid + cfg.getIncrement();
                    if (nextBid > cfg.getMaxBid()) continue;
                    if (winner == null || cfg.getRegisteredAt().isBefore(winner.getRegisteredAt())) {
                        winner = cfg;
                    }
                }
                if (winner != null) {
                    double autoBidAmount = currentHighestBid + winner.getIncrement();
                    System.out.println("[AUTO-BID] " + winner.getBidder().getUsername() + " tu dong dat gia: " + autoBidAmount);
                    currentHighestBid = autoBidAmount;
                    currentLeader     = winner.getBidder();
                    bidHistory.add(new BidTransaction(winner.getBidder(), autoBidAmount, LocalDateTime.now()));
                    applyAntiSnipe();
                    if (notifier != null) {
                        notifier.broadcast(new BidUpdateEvent(auctionId, autoBidAmount, winner.getBidder(), LocalDateTime.now().toString()));
                    }
                    anyBidPlaced = true;
                }
            } finally {
                lock.unlock();
            }
            if (anyBidPlaced) {
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    // ── CLOSE ─────────────────────────────────────────────────────────────────
    public synchronized void closeAuction() {
        if (status == AuctionStatus.RUNNING) {
            status = AuctionStatus.FINISHED;
            System.out.println(currentLeader != null
                    ? "Phien ket thuc, nguoi thang: " + currentLeader.getUsername() + " gia: " + currentHighestBid
                    : "Phien ket thuc, khong co nguoi dat gia");
        }
    }

    // ── GETTERS / SETTERS ─────────────────────────────────────────────────────
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
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public List<BidTransaction> getBidHistory() { return bidHistory; }
    public void setBidHistory(List<BidTransaction> bidHistory) { this.bidHistory = bidHistory; }
    public int getAutoBidCount() {
        lock.lock(); try { return autoBidConfigs.size(); } finally { lock.unlock(); }
    }
}
