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
 * Model đấu giá — rebuild version with ReentrantLock, Anti-Snipe, and full state machine.
 *
 * MERGE NOTE: This is 100% from rebuildver1. The master branch stored Auction as a binary
 * — the rebuild version is the canonical, correct implementation.
 *
 * Key features:
 *  - ReentrantLock (fair) instead of synchronized — avoids starvation, is reentrant
 *  - placeBid() returns BidResponse so test cases can assert on it
 *  - OPEN → RUNNING auto-transition on first bid
 *  - Expired-session auto-close inside placeBid()
 *  - BidUpdateEvent uses (String auctionId, ...) — not Auction object — avoids circular serialization
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

    // Optimistic Locking version — kept in sync with the DB column
    private int version;

    // Fair ReentrantLock: prevents starvation under high concurrency
    private final Lock lock = new ReentrantLock(true);

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Full constructor — use when creating a new auction session */
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

    /** Convenience constructor — used when only auctionId is known (e.g. ClientHandler) */
    public Auction(String auctionId) {
        this.auctionId = auctionId;
        this.bidHistory = new ArrayList<>();
        this.status = AuctionStatus.OPEN;
        this.version = 0;
    }

    /** DB constructor — used by AuctionDAO.getAllOpenAuctions() when loading from DB */
    public Auction(int id, int itemId, String startTime, String endTime, String status) {
        this.auctionId = String.valueOf(id);
        this.status = parseStatus(status);
        this.currentHighestBid = 0;
        this.bidHistory = new ArrayList<>();
        this.version = 0;
    }

    private AuctionStatus parseStatus(String statusStr) {
        try {
            return AuctionStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return AuctionStatus.OPEN;
        }
    }

    // ── Core business logic ───────────────────────────────────────────────────

    /**
     * Place a bid — thread-safe via ReentrantLock.
     * Returns BidResponse so controllers and tests can assert on the outcome.
     *
     * Flow:
     *  1. Reject if session is FINISHED / CANCELED / PAID
     *  2. Reject if bidAmount <= currentHighestBid
     *  3. Auto-close if endTime has passed
     *  4. Transition OPEN → RUNNING on first bid
     *  5. Update state, record history, broadcast event
     */
    public BidResponse placeBid(Bidder bidder, double bidAmount) {
        lock.lock();
        try {
            // 1. Check session status
            if (status == AuctionStatus.FINISHED
                    || status == AuctionStatus.CANCELED
                    || status == AuctionStatus.PAID) {
                throw new AuctionClosedException("Phiên đấu giá này đã đóng!");
            }

            // 2. Check bid must be strictly higher
            if (bidAmount <= currentHighestBid) {
                throw new InvalidBidException("Mức giá đặt phải cao hơn mức giá hiện tại: " + currentHighestBid);
            }

            // 3. Guard against expired sessions not yet closed by scheduler
            if (endTime != null && LocalDateTime.now().isAfter(endTime)) {
                closeAuction();
                throw new AuctionClosedException("Phiên đấu giá đã hết thời gian!");
            }

            // 4. OPEN → RUNNING on first bid
            if (status == AuctionStatus.OPEN) {
                status = AuctionStatus.RUNNING;
            }

            // 5. Update state
            currentHighestBid = bidAmount;
            currentLeader = bidder;

            BidTransaction transaction = new BidTransaction(bidder, bidAmount, LocalDateTime.now());
            bidHistory.add(transaction);

            System.out.println("[BID] " + bidder.getUsername() + " đặt giá " + bidAmount);

            // 6. Broadcast real-time event
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
     * Close the auction — uses the same ReentrantLock (reentrant, so safe to call from placeBid).
     * Idempotent: safe to call multiple times.
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

    // ── Getters & Setters ────────────────────────────────────────────────────

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