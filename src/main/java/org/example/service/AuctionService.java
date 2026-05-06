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

/**
 * Quản lý logic nghiệp vụ của một phiên đấu giá.
 *
 * Tính năng mới được tích hợp:
 *  - Auto-Bidding : registerAutoBid() / cancelAutoBid()
 *  - Anti-Sniping : tự động gia hạn endTime khi có bid trong X giây cuối
 */
public class AuctionService {

    // ── Các field cũ giữ nguyên ──────────────────────────────────────────────
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

    // ── Auto-Bidding: danh sách cấu hình auto-bid của các bidder ─────────────
    // Dùng List thường vì đã bảo vệ bởi lock, không cần CopyOnWriteArrayList
    private final List<AutoBidConfig> autoBidConfigs = new ArrayList<>();

    // ── Anti-Sniping: cấu hình thời gian ─────────────────────────────────────
    /**
     * Nếu có bid mới trong SNIPE_WINDOW_SECONDS giây cuối →
     * gia hạn thêm SNIPE_EXTENSION_SECONDS giây.
     * Có thể thay bằng setter nếu muốn cấu hình động.
     */
    private static final long SNIPE_WINDOW_SECONDS    = 60;  // 1 phút cuối
    private static final long SNIPE_EXTENSION_SECONDS = 120; // gia hạn 2 phút

    // ── Constructor giữ nguyên chữ ký ─────────────────────────────────────────
    public AuctionService(String auctionId, Item item, Bidder currentLeader,
                          AuctionStatus status, double startingPrice,
                          LocalDateTime startTime, LocalDateTime endTime,
                          AuctionNotifier notifier) {
        this.auctionId       = auctionId;
        this.item            = item;
        this.currentLeader   = currentLeader;
        this.status          = AuctionStatus.OPEN; // luôn bắt đầu từ OPEN
        this.currentHighestBid = startingPrice;
        this.startTime       = startTime;
        this.endTime         = endTime;
        this.bidHistory      = new ArrayList<>();
        this.notifier        = notifier;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PHẦN 1 – PLACE BID (đã có sẵn, bổ sung gọi anti-snipe + trigger auto-bid)
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
            // Kiểm tra trạng thái phiên
            if (status != AuctionStatus.RUNNING) {
                throw new AuctionClosedException("Phien dau gia nay hien khong mo! Vui long quay lai sau!");
            }

            // Kiểm tra thời gian (phòng khi luồng chưa kịp đóng phiên)
            if (LocalDateTime.now().isAfter(endTime)) {
                closeAuction();
                throw new AuctionClosedException("Phien dau gia hien da dong!");
            }

            // Kiểm tra giá hợp lệ
            if (bidAmount <= currentHighestBid) {
                throw new InvalidBidException("Muc gia dat phai cao hon muc gia hien tai: " + currentHighestBid);
            }

            // ── Chấp nhận bid ────────────────────────────────────────────────
            currentHighestBid = bidAmount;
            currentLeader     = bidder;

            BidTransaction transaction = new BidTransaction(bidder, bidAmount, LocalDateTime.now());
            bidHistory.add(transaction);

            System.out.println("[BID] " + bidder.getUsername() + " dat gia " + bidAmount);

            // ── Anti-Sniping ─────────────────────────────────────────────────
            applyAntiSnipe();

            // ── Thông báo realtime cho các client (Observer) ─────────────────
            if (notifier != null) {
                BidUpdateEvent event = new BidUpdateEvent(
                        auctionId, bidAmount, bidder,
                        LocalDateTime.now().toString()
                );
                notifier.broadcast(event);
            }

            // ── Kích hoạt auto-bid của các đối thủ ──────────────────────────
            // Chạy trong một luồng riêng để không block người đặt giá thủ công
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
        LocalDateTime now     = LocalDateTime.now();
        LocalDateTime windowStart = endTime.minusSeconds(SNIPE_WINDOW_SECONDS);

        if (now.isAfter(windowStart)) {
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
     * Nếu bidder đã đăng ký trước đó → ghi đè (hủy config cũ, thêm config mới).
     *
     * @param config cấu hình auto-bid (maxBid, increment, bidder)
     * @throws InvalidBidException nếu maxBid không đủ cao hơn giá hiện tại
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

            // Hủy config cũ nếu bidder đã đăng ký
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
            autoBidConfigs.removeIf(c -> c.getBidder().getId() == (bidder.getId()));
            System.out.println("[AUTO-BID] Da huy auto-bid cua: " + bidder.getUsername());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sau khi một bid thủ công hoặc auto-bid được chấp nhận,
     * kích hoạt auto-bid của các đối thủ (không phải người vừa thắng).
     *
     * Chạy trong luồng riêng để không block caller.
     * Logic ưu tiên: đăng ký sớm hơn được xét trước (FIFO theo registeredAt).
     *
     * @param lastBidder người vừa đặt giá thắng (bỏ qua auto-bid của họ)
     */
    private void triggerAutoBids(Bidder lastBidder) {
        // Tạo snapshot để tránh giữ lock quá lâu trong vòng lặp đệ quy
        new Thread(() -> processAutoBids(lastBidder)).start();
    }

    /**
     * Xử lý tuần tự các auto-bid theo thứ tự đăng ký.
     * Vòng lặp tiếp tục cho đến khi không còn auto-bid nào có thể trả giá.
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
                    // Bỏ qua người đang dẫn đầu (họ đã thắng rồi)
                    if (cfg.getBidder().getId() == (currentLeader.getId())) continue;

                    double nextBid = currentHighestBid + cfg.getIncrement();

                    // Kiểm tra không vượt maxBid
                    if (nextBid > cfg.getMaxBid()) continue;

                    // Ưu tiên người đăng ký sớm hơn
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

                    // Anti-sniping cũng áp dụng cho auto-bid
                    applyAntiSnipe();

                    // Broadcast
                    if (notifier != null) {
                        BidUpdateEvent event = new BidUpdateEvent(
                                auctionId, autoBidAmount, winner.getBidder(),
                                LocalDateTime.now().toString()
                        );
                        notifier.broadcast(event);
                    }

                    anyBidPlaced = true;
                }

            } finally {
                lock.unlock();
            }

            // Nhường CPU giữa các vòng lặp để tránh busy-waiting
            if (anyBidPlaced) {
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PHẦN 4 – closeAuction (giữ nguyên như cũ)
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
    //  GETTERS / SETTERS (giữ nguyên như cũ)
    // ═══════════════════════════════════════════════════════════════════════════

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

    /** Trả về số lượng auto-bid config đang hoạt động (dùng cho test). */
    public int getAutoBidCount() {
        lock.lock();
        try { return autoBidConfigs.size(); }
        finally { lock.unlock(); }
    }
}
