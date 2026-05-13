package org.example.service;

import org.example.exception.AuctionClosedException;
import org.example.exception.InvalidBidException;
import org.example.model.AuctionStatus;
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
 * Quản lý logic nghiệp vụ của một phiên đấu giá (in-memory).
 *
 * Tính năng:
 *  - Đặt giá thủ công với kiểm tra trạng thái và thời gian
 *  - Auto-Bidding : registerAutoBid() / cancelAutoBid()
 *  - Anti-Sniping : tự động gia hạn endTime khi có bid trong X giây cuối
 *
 * Import đã cập nhật:
 *  - AuctionStatus từ org.example.model (thay vì org.example.service)
 *  - AuctionNotifier, BidUpdateEvent từ org.example.observer
 *  - BidTransaction từ org.example.model
 */
public class AuctionService {

    // ── Fields ───────────────────────────────────────────────────────────────
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

    // ── Auto-Bidding ──────────────────────────────────────────────────────────
    private final List<AutoBidConfig> autoBidConfigs = new ArrayList<>();

    // ── Anti-Sniping ──────────────────────────────────────────────────────────
    private static final long SNIPE_WINDOW_SECONDS    = 60;  // 1 phút cuối
    private static final long SNIPE_EXTENSION_SECONDS = 120; // gia hạn 2 phút

    // ── Constructor ───────────────────────────────────────────────────────────
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

    // ═══════════════════════════════════════════════════════════════════════════
    //  PHẦN 1 – PLACE BID
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Xử lý logic đặt giá của người dùng.
     * Sau khi bid hợp lệ được chấp nhận:
     *   1. Anti-sniping kiểm tra và gia hạn nếu cần.
     *   2. Auto-bidding của các đối thủ được kích hoạt.
     */
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

            // Chấp nhận bid
            currentHighestBid = bidAmount;
            currentLeader     = bidder;
            bidHistory.add(new BidTransaction(bidder, bidAmount, LocalDateTime.now()));

            System.out.println("[BID] " + bidder.getUsername() + " dat gia " + bidAmount);

            // Anti-Sniping
            applyAntiSnipe();

            // Broadcast realtime
            if (notifier != null) {
                BidUpdateEvent event = new BidUpdateEvent(
                        null, bidAmount, bidder, LocalDateTime.now().toString()
                );
                notifier.broadcast(event);
            }

            // Kích hoạt auto-bid đối thủ (luồng riêng)
            triggerAutoBids(bidder);

        } finally {
            lock.unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PHẦN 2 – ANTI-SNIPING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Nếu bid mới đến trong SNIPE_WINDOW_SECONDS giây cuối → gia hạn phiên.
     * Phải được gọi bên trong lock.
     */
    private void applyAntiSnipe() {
        LocalDateTime windowStart = endTime.minusSeconds(SNIPE_WINDOW_SECONDS);
        if (LocalDateTime.now().isAfter(windowStart)) {
            LocalDateTime newEndTime = endTime.plusSeconds(SNIPE_EXTENSION_SECONDS);
            System.out.println("[ANTI-SNIPE] Phat hien bid trong " + SNIPE_WINDOW_SECONDS
                    + "s cuoi! Gia han phien den: " + newEndTime);
            endTime = newEndTime;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PHẦN 3 – AUTO-BIDDING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Đăng ký auto-bid cho một bidder.
     * Nếu bidder đã đăng ký trước đó → ghi đè.
     */
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

    /**
     * Hủy đăng ký auto-bid của một bidder.
     * An toàn khi gọi ngay cả khi bidder chưa đăng ký.
     */
    public void cancelAutoBid(Bidder bidder) {
        lock.lock();
        try {
            autoBidConfigs.removeIf(c -> c.getBidder().getId() == bidder.getId());
            System.out.println("[AUTO-BID] Da huy auto-bid cua: " + bidder.getUsername());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Kích hoạt auto-bid của các đối thủ sau khi có bid mới.
     * Chạy trong luồng riêng để không block caller.
     */
    private void triggerAutoBids(Bidder lastBidder) {
        new Thread(() -> processAutoBids(lastBidder)).start();
    }

    /**
     * Xử lý tuần tự các auto-bid theo thứ tự đăng ký (FIFO).
     * Người đăng ký sớm hơn được ưu tiên xét trước.
     */
    private void processAutoBids(Bidder skipBidder) {
        boolean anyBidPlaced = true;

        while (anyBidPlaced) {
            anyBidPlaced = false;

            lock.lock();
            try {
                // Dừng nếu phiên đã kết thúc
                if (status != AuctionStatus.RUNNING) break;

                // Tìm auto-bid đủ điều kiện (không phải người đang dẫn đầu,
                // có đủ maxBid, đăng ký sớm nhất được ưu tiên)
                AutoBidConfig winner = null;
                for (AutoBidConfig cfg : autoBidConfigs) {
                    // Bỏ qua người đang dẫn đầu (do đã thắng rồi)
                    if (cfg.getBidder().getId() == currentLeader.getId()) continue;

                    double nextBid = currentHighestBid + cfg.getIncrement();

                    // Kiểm tra để ko vượt quá maxBid
                    if (nextBid > cfg.getMaxBid()) continue;

                    // ưu tiên người đăng kí sơm hơn
                    if (winner == null || cfg.getRegisteredAt().isBefore(winner.getRegisteredAt())) {
                        winner = cfg;
                    }
                }

                if (winner != null) {
                    double autoBidAmount = currentHighestBid + winner.getIncrement();
                    System.out.println("[AUTO-BID] " + winner.getBidder().getUsername()
                            + " tu dong dat gia: " + autoBidAmount);
                    // Cập nhật trực tiếp (đã ở trong lock, không gọi placeBid để tránh deadlock)
                    currentHighestBid = autoBidAmount;
                    currentLeader     = winner.getBidder();
                    bidHistory.add(new BidTransaction(winner.getBidder(), autoBidAmount, LocalDateTime.now()));

                    applyAntiSnipe();

                    if (notifier != null) {
                        BidUpdateEvent event = new BidUpdateEvent(
                                null, autoBidAmount, winner.getBidder(),
                                LocalDateTime.now().toString()
                        );
                        notifier.broadcast(event);
                    }

                    anyBidPlaced = true;
                }

            } finally {
                lock.unlock();
            }

            if (anyBidPlaced) {
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PHẦN 4 – CLOSE AUCTION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Đóng phiên đấu giá và xác định người thắng cuộc.
     */

    public synchronized void closeAuction() {
        if (status == AuctionStatus.RUNNING) {
            status = AuctionStatus.FINISHED;
            if (currentLeader != null) {
                System.out.println("Phien dau gia ket thuc, nguoi thang cuoc la " + currentLeader.getUsername()
                        + " voi muc gia " + currentHighestBid);
            } else {
                System.out.println("Phien dau gia ket thuc, khong co nguoi dat gia");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  GETTERS / SETTERS
    // ═══════════════════════════════════════════════════════════════════════════

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

    /** Trả về số lượng auto-bid config đang hoạt động (dùng cho test). */
    public int getAutoBidCount() {
        lock.lock();
        try { return autoBidConfigs.size(); }
        finally { lock.unlock(); }
    }
}