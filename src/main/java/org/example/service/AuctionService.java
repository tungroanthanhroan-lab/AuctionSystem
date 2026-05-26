package org.example.service;

import org.example.dao.AuctionDAO;
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


public class AuctionService {
    private final AuctionDAO auctionDAO;
    private final AuctionNotifier auctionNotifier;

    // Quản lý các phiên đấu giá đang diễn ra trên RAM
    private final ConcurrentHashMap<String, Auction> activeAuctions;
    // Quản lý cấu hình auto-bid cho từng phiên
    private final ConcurrentHashMap<String, List<AutoBidConfig>> autoBids;

    private static final long SNIPE_WINDOW_SECONDS = 60;
    private static final long SNIPE_EXTENSION_SECONDS = 120;

    public AuctionService(AuctionDAO auctionDAO, AuctionNotifier auctionNotifier) {
        this.auctionDAO = auctionDAO;
        this.auctionNotifier = auctionNotifier;
        this.activeAuctions = new ConcurrentHashMap<>();
        this.autoBids = new ConcurrentHashMap<>();
        loadActiveAuctionsFromDB();
    }

    private void loadActiveAuctionsFromDB() {
        List<Auction> openAuctions = auctionDAO.getAllOpenAuctions();
        if (openAuctions != null) {
            for (Auction auction : openAuctions) {
                activeAuctions.put(auction.getAuctionId(), auction);
            }
        }
        System.out.println("Đã tải " + activeAuctions.size() + " phiên đấu giá lên bộ nhớ.");
    }

    public boolean placeBid(String auctionId, String bidderName, double amount) {
        Auction auction = activeAuctions.get(auctionId);
        if (auction == null) {
            System.out.println("Phiên đấu giá không tồn tại hoặc đã đóng.");
            return false;
        }

        // KHÓA ĐỐI TƯỢNG (Locking): Đảm bảo tại 1 thời điểm, chỉ 1 người được xét duyệt giá
        synchronized (auction) {
            try {
                if (auction.getStatus() != AuctionStatus.OPEN && auction.getStatus() != AuctionStatus.RUNNING) {
                    return false;
                }

                if (amount <= auction.getCurrentHighestBid()) {
                    return false;
                }

                // 1. Logic Anti-Sniping
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime endTime = auction.getEndTime();
                if (endTime != null) {
                    if (now.isAfter(endTime)) {
                        auction.setStatus(AuctionStatus.FINISHED);
                        return false;
                    }
                    if (java.time.Duration.between(now, endTime).getSeconds() <= SNIPE_WINDOW_SECONDS) {
                        auction.setEndTime(endTime.plusSeconds(SNIPE_EXTENSION_SECONDS));
                        System.out.println("[ANTI-SNIPE] Gia hạn phiên " + auctionId + " đến: " + auction.getEndTime());
                    }
                }

                // 2. Cập nhật DB (Sử dụng Optimistic Locking)
                boolean isDbUpdated = auctionDAO.updateBidWithOptimisticLock(auctionId, bidderName, amount, auction.getVersion());

                if (isDbUpdated) {
                    // 3. Cập nhật RAM
                    if (auction.getStatus() == AuctionStatus.OPEN) {
                        auction.setStatus(AuctionStatus.RUNNING);
                    }
                    auction.setCurrentHighestBid(amount);
                    Bidder bidder = new Bidder(bidderName);
                    auction.setCurrentLeader(bidder);
                    auction.setVersion(auction.getVersion() + 1);

                    // 4. Broadcast Real-time
                    BidUpdateEvent event = new BidUpdateEvent(auctionId, amount, bidder, String.valueOf(System.currentTimeMillis()));
                    auctionNotifier.broadcast(event);

                    // 5. Kích hoạt Auto-Bidding
                    triggerAutoBids(auctionId, bidderName);
                    return true;
                } else {
                    System.out.println("Lỗi đồng thời (Conflict): Dữ liệu DB đã bị thay đổi bởi người khác.");
                    return false;
                }

            } catch (Exception e) {
                System.out.println("Lỗi hệ thống khi đặt giá: " + e.getMessage());
                return false;
            }
        }
    }

    public void registerAutoBid(String auctionId, AutoBidConfig config) {
        Auction auction = activeAuctions.get(auctionId);
        if (auction == null) {
            return;
        }
        // Ném lỗi trực tiếp nếu phiên không còn mở/chạy để Test case assert thành công
        if (auction.getStatus() != AuctionStatus.OPEN && auction.getStatus() != AuctionStatus.RUNNING) {
            throw new org.example.exception.AuctionClosedException("Khong the dang ky auto-bid khi phien da dong!");
        }        if (config.getMaxBid() <= auction.getCurrentHighestBid()) {
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
                String currentLeaderName = auction.getCurrentLeader() != null ? auction.getCurrentLeader().getUsername() : "";

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

                    boolean dbUpdated = auctionDAO.updateBidWithOptimisticLock(auctionId, winner.getBidder().getUsername(), autoBidAmount, auction.getVersion());

                    if (dbUpdated) {
                        System.out.println("[AUTO-BID] " + winner.getBidder().getUsername() + " tự động đặt giá: " + autoBidAmount);
                        auction.setCurrentHighestBid(autoBidAmount);
                        auction.setCurrentLeader(winner.getBidder());
                        auction.setVersion(auction.getVersion() + 1);

                        // Anti-snipe lại lần nữa
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime endTime = auction.getEndTime();
                        if (endTime != null && java.time.Duration.between(now, endTime).getSeconds() <= SNIPE_WINDOW_SECONDS) {
                            auction.setEndTime(endTime.plusSeconds(SNIPE_EXTENSION_SECONDS));
                        }

                        auctionNotifier.broadcast(new BidUpdateEvent(auctionId, autoBidAmount, winner.getBidder(), String.valueOf(System.currentTimeMillis())));
                        anyBidPlaced = true;
                    }
                }
            }
            if (anyBidPlaced) {
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    public List<Auction> getActiveAuctionsList() {
        return List.copyOf(activeAuctions.values());
    }

    public boolean createAuction(int itemId, double startingPrice, String endTime) {
        try {
            LocalDateTime end = LocalDateTime.parse(endTime);
            LocalDateTime start = LocalDateTime.now();

            Item item = new Item();
            item.setId(itemId);

            Auction newAuction = new Auction(
                    null, item, null, AuctionStatus.OPEN, startingPrice, start, end, auctionNotifier
            );

            boolean saved = auctionDAO.insertAuction(newAuction);
            if (saved) {
                activeAuctions.put(newAuction.getAuctionId(), newAuction);
                System.out.println("[AuctionService] Phiên đấu giá mới tạo: id=" + newAuction.getAuctionId());
                return true;
            }
            return false;
        } catch (java.time.format.DateTimeParseException e) {
            System.err.println("[AuctionService] endTime sai format ISO (yyyy-MM-ddTHH:mm): " + endTime);
            return false;
        } catch (Exception e) {
            System.err.println("[AuctionService] Lỗi tạo phiên đấu giá: " + e.getMessage());
            return false;
        }
    }

    // --- Các hàm hỗ trợ cho file Test ---
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