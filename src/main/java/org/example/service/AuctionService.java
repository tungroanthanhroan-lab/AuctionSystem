package org.example.service;

import org.example.dao.AuctionDAO;
import org.example.dao.UserDAO;
import org.example.exception.InvalidBidException;
import org.example.model.Auction;
import org.example.model.AuctionStatus;
import org.example.model.Bidder;
import org.example.model.Item;
import org.example.observer.AuctionNotifier;
import org.example.observer.BidUpdateEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AuctionService — all auction business logic.
 *
 * MERGE NOTES:
 *  - Constructor: now takes (AuctionDAO, AuctionNotifier, UserDAO) — master's signature.
 *    The rebuild only took (AuctionDAO, AuctionNotifier) because it had no HOLD BALANCE.
 *    AuctionServer.java must be updated to pass userDAO here (see server package).
 *
 *  - placeBid(): uses master's HOLD BALANCE flow (placeBidWithHold DB transaction) instead of
 *    rebuild's simpler updateBidWithOptimisticLock. Anti-Snipe logic from rebuild is also kept.
 *
 *  - Auto-Bid: kept in full from rebuild (registerAutoBid, cancelAutoBid, triggerAutoBids).
 *    The autoBid loop calls updateBidWithOptimisticLock (no money held for auto-bids — fine
 *    for a student project; production would need held_balance for auto-bids too).
 *
 *  - closeAuction(): master's full payment flow (deductBalanceOnWin + updateBalance for seller).
 *
 *  - createAuctionWithNewItem(): from master (creates both item and auction in one transaction).
 *    isAuctionOwner() and getAuctionsBySellerId() also from master (needed by UI commands).
 *
 *  - Test helpers (getAuction, getAutoBidCount, addAuctionForTest): from rebuild.
 */
public class AuctionService {

    private final AuctionDAO auctionDAO;
    private final AuctionNotifier auctionNotifier;
    private final UserDAO userDAO;  // HOLD BALANCE: inject to check / deduct balances

    private final ConcurrentHashMap<String, Auction> activeAuctions;
    private final ConcurrentHashMap<String, List<AutoBidConfig>> autoBids;

    // Anti-Snipe constants (from rebuild)
    private static final long SNIPE_WINDOW_SECONDS    = 60;
    private static final long SNIPE_EXTENSION_SECONDS = 120;

    public AuctionService(AuctionDAO auctionDAO, AuctionNotifier auctionNotifier, UserDAO userDAO) {
        this.auctionDAO      = auctionDAO;
        this.auctionNotifier = auctionNotifier;
        this.userDAO         = userDAO;
        this.activeAuctions  = new ConcurrentHashMap<>();
        this.autoBids        = new ConcurrentHashMap<>();
        loadActiveAuctionsFromDB();
    }

    private void loadActiveAuctionsFromDB() {
        List<Auction> openAuctions = auctionDAO.getAllOpenAuctions();
        if (openAuctions != null) {
            for (Auction auction : openAuctions) {
                activeAuctions.put(auction.getAuctionId(), auction);
            }
        }
        System.out.println("Đã tải " + activeAuctions.size() + " phiên đấu giá lên hệ thống.");
    }

    // ── Core bid logic ────────────────────────────────────────────────────────

    /**
     * Place a bid with HOLD BALANCE + Anti-Snipe + Optimistic Locking.
     *
     * Flow:
     *  1. Get auction from RAM.
     *  2. synchronized(auction) — one thread per auction at a time.
     *  3. Validate: amount > currentHighestBid.
     *  4. Anti-Snipe: if within SNIPE_WINDOW_SECONDS of endTime → extend endTime.
     *  5. Check available_balance (balance - held_balance) from DB.
     *  6. DB transaction: placeBidWithHold (hold new + release prev + update auction).
     *  7. Update RAM, broadcast, trigger auto-bids.
     */
    public boolean placeBid(String auctionId, String bidderName, double amount) {
        Auction auction = activeAuctions.get(auctionId);
        if (auction == null) {
            System.out.println("[AuctionService] Phiên đấu giá không tồn tại hoặc đã đóng: " + auctionId);
            return false;
        }

        synchronized (auction) {
            try {
                if (auction.getStatus() != AuctionStatus.OPEN
                        && auction.getStatus() != AuctionStatus.RUNNING) {
                    return false;
                }

                if (amount <= auction.getCurrentHighestBid()) {
                    System.out.println("[AuctionService] Giá " + amount + " không cao hơn giá hiện tại "
                            + auction.getCurrentHighestBid());
                    return false;
                }

                // Anti-Snipe (from rebuild)
                LocalDateTime now     = LocalDateTime.now();
                LocalDateTime endTime = auction.getEndTime();
                if (endTime != null) {
                    if (now.isAfter(endTime)) {
                        auction.setStatus(AuctionStatus.FINISHED);
                        return false;
                    }
                    long secondsLeft = java.time.Duration.between(now, endTime).getSeconds();
                    if (secondsLeft <= SNIPE_WINDOW_SECONDS) {
                        auction.setEndTime(endTime.plusSeconds(SNIPE_EXTENSION_SECONDS));
                        System.out.println("[ANTI-SNIPE] Gia hạn phiên " + auctionId
                                + " đến: " + auction.getEndTime());
                    }
                }

                // HOLD BALANCE: check available_balance before touching DB
                double availableBalance = userDAO.getAvailableBalance(bidderName);
                if (availableBalance < 0) {
                    System.out.println("[AuctionService] Không lấy được số dư của: " + bidderName);
                    return false;
                }
                if (availableBalance < amount) {
                    System.out.println("[AuctionService] " + bidderName + " không đủ available_balance: "
                            + availableBalance + " < " + amount);
                    return false;
                }

                String prevLeader = (auction.getCurrentLeader() != null)
                        ? auction.getCurrentLeader().getUsername() : null;
                double prevAmount = auction.getCurrentHighestBid();

                // Atomic DB transaction: hold + release + optimistic lock
                boolean dbOk = auctionDAO.placeBidWithHold(
                        auctionId, bidderName, amount, auction.getVersion(),
                        prevLeader, prevAmount
                );

                if (dbOk) {
                    auction.setCurrentHighestBid(amount);
                    Bidder bidder = new Bidder(bidderName);
                    auction.setCurrentLeader(bidder);
                    auction.setVersion(auction.getVersion() + 1);
                    if (auction.getStatus() == AuctionStatus.OPEN) {
                        auction.setStatus(AuctionStatus.RUNNING);
                    }

                    BidUpdateEvent event = new BidUpdateEvent(
                            auctionId, amount, bidder, String.valueOf(System.currentTimeMillis()));
                    auctionNotifier.broadcast(event);

                    triggerAutoBids(auctionId, bidderName);

                    System.out.println("[AuctionService] Đặt giá thành công: " + bidderName
                            + " | auction=" + auctionId + " | amount=" + amount);
                    return true;
                } else {
                    System.out.println("[AuctionService] DB từ chối bid (conflict hoặc insufficient balance): "
                            + bidderName + " | auction=" + auctionId);
                    return false;
                }

            } catch (Exception e) {
                System.out.println("[AuctionService] Lỗi hệ thống khi đặt giá: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
    }

    // ── Auto-Bid (from rebuild) ───────────────────────────────────────────────

    public void registerAutoBid(String auctionId, AutoBidConfig config) {
        Auction auction = activeAuctions.get(auctionId);
        if (auction == null) return;
        if (auction.getStatus() != AuctionStatus.OPEN && auction.getStatus() != AuctionStatus.RUNNING) {
            throw new org.example.exception.AuctionClosedException("Không thể đăng ký auto-bid khi phiên đã đóng!");
        }
        if (config.getMaxBid() <= auction.getCurrentHighestBid()) {
            throw new InvalidBidException("maxBid (" + config.getMaxBid() + ") phải lớn hơn giá hiện tại");
        }
        cancelAutoBid(auctionId, config.getBidder());
        autoBids.computeIfAbsent(auctionId, k -> new CopyOnWriteArrayList<>()).add(config);
        System.out.println("[AUTO-BID] Đã đăng ký: " + config);
    }

    public void cancelAutoBid(String auctionId, Bidder bidder) {
        List<AutoBidConfig> configs = autoBids.get(auctionId);
        if (configs != null) {
            configs.removeIf(c -> c.getBidder().getUsername().equals(bidder.getUsername()));
            System.out.println("[AUTO-BID] Đã hủy auto-bid của: " + bidder.getUsername());
        }
    }

    private void triggerAutoBids(String auctionId, String skipBidderName) {
        new Thread(() -> processAutoBids(auctionId, skipBidderName)).start();
    }

    private void processAutoBids(String auctionId, String skipBidderName) {
        boolean anyBidPlaced = true;
        while (anyBidPlaced) {
            anyBidPlaced = false;
            Auction auction = activeAuctions.get(auctionId);
            if (auction == null) return;

            synchronized (auction) {
                if (auction.getStatus() != AuctionStatus.RUNNING) break;

                List<AutoBidConfig> configs = autoBids.getOrDefault(auctionId, new ArrayList<>());
                AutoBidConfig winner = null;
                double currentPrice = auction.getCurrentHighestBid();
                String currentLeaderName = auction.getCurrentLeader() != null
                        ? auction.getCurrentLeader().getUsername() : "";

                for (AutoBidConfig cfg : configs) {
                    if (cfg.getBidder().getUsername().equals(currentLeaderName)) continue;
                    double nextBid = currentPrice + cfg.getIncrement();
                    if (nextBid > cfg.getMaxBid()) continue;
                    if (winner == null || cfg.getRegisteredAt().isBefore(winner.getRegisteredAt())) {
                        winner = cfg;
                    }
                }

                if (winner != null) {
                    double autoBidAmount = currentPrice + winner.getIncrement();
                    boolean dbUpdated = auctionDAO.updateBidWithOptimisticLock(
                            auctionId, winner.getBidder().getUsername(),
                            autoBidAmount, auction.getVersion()
                    );
                    if (dbUpdated) {
                        System.out.println("[AUTO-BID] " + winner.getBidder().getUsername()
                                + " tự động đặt giá: " + autoBidAmount);
                        auction.setCurrentHighestBid(autoBidAmount);
                        auction.setCurrentLeader(winner.getBidder());
                        auction.setVersion(auction.getVersion() + 1);

                        // Anti-snipe for auto-bids too
                        LocalDateTime now2    = LocalDateTime.now();
                        LocalDateTime endTime = auction.getEndTime();
                        if (endTime != null
                                && java.time.Duration.between(now2, endTime).getSeconds() <= SNIPE_WINDOW_SECONDS) {
                            auction.setEndTime(endTime.plusSeconds(SNIPE_EXTENSION_SECONDS));
                        }

                        auctionNotifier.broadcast(new BidUpdateEvent(
                                auctionId, autoBidAmount, winner.getBidder(),
                                String.valueOf(System.currentTimeMillis())));
                        anyBidPlaced = true;
                    }
                }
            }
            if (anyBidPlaced) {
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        }
    }

    // ── Auction management (from master) ─────────────────────────────────────

    /**
     * Close auction with full payment settlement (HOLD BALANCE):
     *  1. Get winner info from DB.
     *  2. Deduct winner's balance + release held.
     *  3. Credit seller.
     *  4. Persist FINISHED status.
     *  5. Remove from activeAuctions.
     */
    public boolean closeAuction(String auctionId) {
        Auction auction = activeAuctions.get(auctionId);
        if (auction == null) {
            System.out.println("[AuctionService] closeAuction: Không tìm thấy phiên " + auctionId);
            return false;
        }

        String[] winnerInfo = auctionDAO.getWinnerInfo(auctionId);
        if (winnerInfo != null) {
            String winner    = winnerInfo[0];
            double bidAmount = Double.parseDouble(winnerInfo[1]);
            String seller    = winnerInfo[2];

            System.out.println("[AuctionService] Phiên " + auctionId + " kết thúc."
                    + " Winner: " + winner + " | Amount: " + bidAmount + " | Seller: " + seller);

            boolean deducted = userDAO.deductBalanceOnWin(winner, bidAmount);
            if (!deducted) {
                System.err.println("[AuctionService] Cảnh báo: deductBalanceOnWin thất bại cho " + winner);
            } else {
                System.out.println("[AuctionService] Đã trừ " + bidAmount + " từ balance của " + winner);
            }

            if (!winner.equals(seller)) {
                boolean credited = userDAO.updateBalance(seller, bidAmount);
                if (credited) {
                    System.out.println("[AuctionService] Đã cộng " + bidAmount + " cho seller " + seller);
                } else {
                    System.err.println("[AuctionService] Cảnh báo: Không thể cộng tiền cho seller: " + seller);
                }
            }
        } else {
            System.out.println("[AuctionService] Phiên " + auctionId + " kết thúc — không có ai đặt giá.");
        }

        auction.closeAuction();
        boolean dbOk = auctionDAO.closeAuction(auctionId);
        if (!dbOk)
            System.err.println("[AuctionService] closeAuction: DB không cập nhật được phiên " + auctionId);

        activeAuctions.remove(auctionId);
        System.out.println("[AuctionService] Đã đóng phiên đấu giá " + auctionId);
        return true;
    }

    /** Create auction by title (UI flow — creates item + auction in one DB transaction). */
    public boolean createAuctionWithNewItem(String title, String description,
                                            double startingPrice, String endTime, int sellerId) {
        try {
            if (title == null || title.trim().isEmpty()) return false;
            if (startingPrice < 0) return false;
            if (description == null) description = "";
            if (endTime == null || endTime.trim().isEmpty()) endTime = "2099-12-31T23:59";

            Auction auction = auctionDAO.createAuctionWithNewItem(
                    title.trim(), description.trim(), startingPrice, endTime.trim(), sellerId);

            if (auction == null) return false;
            activeAuctions.put(auction.getAuctionId(), auction);
            System.out.println("Đã tạo phiên đấu giá mới: " + auction.getAuctionId());
            return true;
        } catch (Exception e) {
            System.out.println("Lỗi tạo phiên đấu giá: " + e.getMessage());
            return false;
        }
    }

    /** Create auction by itemId (legacy / CLI flow). */
    public boolean createAuction(int itemId, double startingPrice, String endTime) {
        try {
            LocalDateTime end   = LocalDateTime.parse(endTime);
            LocalDateTime start = LocalDateTime.now();
            Item item = new Item();
            item.setId(itemId);

            Auction newAuction = new Auction(
                    null, item, null, AuctionStatus.OPEN,
                    startingPrice, start, end, auctionNotifier
            );

            boolean saved = auctionDAO.insertAuction(newAuction);
            if (saved) {
                activeAuctions.put(newAuction.getAuctionId(), newAuction);
                System.out.println("[AuctionService] Phiên đấu giá mới: id=" + newAuction.getAuctionId());
                return true;
            }
            return false;
        } catch (java.time.format.DateTimeParseException e) {
            System.err.println("[AuctionService] endTime sai format: " + endTime);
            return false;
        } catch (Exception e) {
            System.err.println("[AuctionService] Lỗi tạo phiên: " + e.getMessage());
            return false;
        }
    }

    public boolean isAuctionOwner(String auctionId, int userId) {
        return auctionDAO.isAuctionOwner(auctionId, userId);
    }

    public List<Auction> getAuctionsBySellerId(int sellerId) {
        return auctionDAO.getAuctionsBySellerId(sellerId);
    }

    public List<Auction> getActiveAuctionsList() {
        return List.copyOf(activeAuctions.values());
    }

    // ── Test helpers (from rebuild) ───────────────────────────────────────────

    public Auction getAuction(String auctionId) {
        return activeAuctions.get(auctionId);
    }

    public int getAutoBidCount(String auctionId) {
        List<AutoBidConfig> configs = autoBids.get(auctionId);
        return configs != null ? configs.size() : 0;
    }

    public void addAuctionForTest(Auction auction) {
        activeAuctions.put(auction.getAuctionId(), auction);
    }
}