package org.example.service;

import org.example.dao.AuctionDAO;
import org.example.dao.UserDAO;
import org.example.model.Auction;
import org.example.model.AuctionStatus;
import org.example.model.Item;
import org.example.observer.BidUpdateEvent;
import org.example.model.Bidder;
import org.example.observer.AuctionNotifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuctionService — tầng service xử lý toàn bộ nghiệp vụ đấu giá.
 *
 * HOLD BALANCE: Tích hợp cơ chế đóng băng tiền (held_balance) vào flow bid và đóng phiên.
 * UserDAO được inject để truy vấn/thao tác balance mà không cần đi qua UserService
 * (tránh circular dependency và giảm round-trip).
 */
public class AuctionService {
    private AuctionDAO auctionDAO;
    private AuctionNotifier auctionNotifier;

    // HOLD BALANCE: Inject UserDAO để kiểm tra available_balance và xử lý deduct/credit tiền
    private UserDAO userDAO;

    // Bộ nhớ Cache lưu trữ các phiên đấu giá đang diễn ra trên RAM
    // Dùng ConcurrentHashMap để Thread-Safe (an toàn khi nhiều luồng truy cập)
    private ConcurrentHashMap<String, Auction> activeAuctions;

    // Inject DAO và Notifier vào qua Constructor
    public AuctionService(AuctionDAO auctionDAO, AuctionNotifier auctionNotifier, UserDAO userDAO) {
        this.auctionDAO = auctionDAO;
        this.auctionNotifier = auctionNotifier;
        this.userDAO = userDAO;
        this.activeAuctions = new ConcurrentHashMap<>();

        // Khởi động server là nạp ngay dữ liệu từ DB lên RAM
        loadActiveAuctionsFromDB();
    }

    private void loadActiveAuctionsFromDB() {
        // Giả sử DAO có hàm lấy danh sách các phiên đang mở
        List<Auction> openAuctions = auctionDAO.getAllOpenAuctions();
        if (openAuctions != null) {
            for (Auction auction : openAuctions) {
                activeAuctions.put(auction.getAuctionId(), auction);
            }
        }
        System.out.println("Đã tải " + activeAuctions.size() + " phiên đấu giá lên hệ thống.");
    }

    /**
     * HOLD BALANCE — Logic đặt giá cốt lõi (Xử lý đồng thời cực kỳ nghiêm ngặt).
     *
     * Flow:
     *   1. Lấy phiên từ RAM; trả về false nếu không tồn tại.
     *   2. synchronized(auction) — chỉ 1 thread được xử lý bid cho phiên này tại 1 thời điểm.
     *   3. Kiểm tra giá mới phải > currentHighestBid.
     *   4. Tính available_balance = balance - held_balance từ DB.
     *      Nếu không đủ → từ chối bid ngay, không mất công gọi DB update.
     *   5. Gọi AuctionDAO.placeBidWithHold() — toàn bộ hold/release/update auction
     *      chạy trong 1 DB transaction để đảm bảo atomic.
     *   6. Nếu DB ok → cập nhật RAM, broadcast thông báo real-time.
     *
     * @param auctionId  id phiên đấu giá
     * @param bidderName username người đặt giá
     * @param amount     số tiền muốn đặt
     * @return true nếu đặt giá thành công
     */
    public boolean placeBid(String auctionId, String bidderName, double amount) {
        // 1. Lấy phiên đấu giá từ bộ nhớ RAM
        Auction auction = activeAuctions.get(auctionId);

        if (auction == null) {
            System.out.println("[AuctionService] Phiên đấu giá không tồn tại hoặc đã đóng: " + auctionId);
            return false;
        }

        // 2. KHÓA ĐỐI TƯỢNG — chỉ 1 luồng được xét duyệt giá cho phiên này tại 1 thời điểm
        synchronized (auction) {
            try {
                // 3. Kiểm tra logic nghiệp vụ: Giá mới phải lớn hơn giá hiện tại
                if (amount <= auction.getCurrentHighestBid()) {
                    System.out.println("[AuctionService] Giá " + amount + " không cao hơn giá hiện tại "
                            + auction.getCurrentHighestBid());
                    return false;
                }

                // 4. HOLD BALANCE: Kiểm tra available_balance trước khi gọi DB transaction
                //    available_balance = balance - held_balance
                //    Đọc thẳng từ DB để đảm bảo tính nhất quán (không cache trên RAM)
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

                // 5. Lấy thông tin người dẫn đầu hiện tại để release held khi bị vượt
                String prevLeader = (auction.getCurrentLeader() != null)
                        ? auction.getCurrentLeader().getUsername()
                        : null;
                // Lưu lại prevAmount TRƯỚC khi update để release đúng số tiền
                double prevAmount = auction.getCurrentHighestBid();

                // 6. Gọi DB transaction: hold tiền mới + release tiền cũ + update auction (Optimistic Locking)
                //    Toàn bộ 3 bước chạy atomic trong 1 transaction — nếu bất kỳ bước nào fail → rollback
                boolean isDbUpdated = auctionDAO.placeBidWithHold(
                        auctionId, bidderName, amount, auction.getVersion(),
                        prevLeader, prevAmount
                );

                if (isDbUpdated) {
                    // 7. DB thành công → Cập nhật trạng thái trên RAM
                    auction.setCurrentHighestBid(amount);
                    auction.setCurrentLeader(new Bidder(bidderName));
                    auction.setVersion(auction.getVersion() + 1); // Đồng bộ version RAM với DB
                    if (auction.getStatus() == AuctionStatus.OPEN) {
                        auction.setStatus(AuctionStatus.RUNNING); // Phiên chuyển sang RUNNING khi có bid đầu tiên
                    }

                    // 8. PHÁT LOA THÔNG BÁO REAL-TIME cho tất cả client đang theo dõi
                    BidUpdateEvent event = new BidUpdateEvent(
                            auction,
                            amount,
                            auction.getCurrentLeader(),
                            String.valueOf(System.currentTimeMillis())
                    );
                    auctionNotifier.broadcast(event);

                    System.out.println("[AuctionService] Đặt giá thành công: " + bidderName
                            + " | auction=" + auctionId + " | amount=" + amount
                            + " | held_balance của " + bidderName + " tăng thêm " + amount
                            + (prevLeader != null ? " | released " + prevAmount + " cho " + prevLeader : ""));
                    return true;

                } else {
                    // DB báo false: có thể do conflict version hoặc không đủ tiền tại DB
                    System.out.println("[AuctionService] DB từ chối bid (conflict version hoặc insufficient balance): "
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

    /**
     * Seller/Admin tạo phiên đấu giá mới bằng tên sản phẩm.
     */
    public boolean createAuctionWithNewItem(String title,
                                            String description,
                                            double startingPrice,
                                            String endTime,
                                            int sellerId) {
        try {
            if (title == null || title.trim().isEmpty()) {
                return false;
            }

            if (startingPrice < 0) {
                return false;
            }

            if (description == null) {
                description = "";
            }

            if (endTime == null || endTime.trim().isEmpty()) {
                endTime = "2099-12-31T23:59";
            }

            Auction auction = auctionDAO.createAuctionWithNewItem(
                    title.trim(),
                    description.trim(),
                    startingPrice,
                    endTime.trim(),
                    sellerId
            );

            if (auction == null) {
                return false;
            }

            activeAuctions.put(auction.getAuctionId(), auction);

            System.out.println("Đã tạo phiên đấu giá mới: " + auction.getAuctionId());

            return true;

        } catch (Exception e) {
            System.out.println("Lỗi tạo phiên đấu giá: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Kiểm tra user có phải chủ phiên đấu giá không.
     */
    public boolean isAuctionOwner(String auctionId, int userId) {
        return auctionDAO.isAuctionOwner(auctionId, userId);
    }

    /**
     * Lấy danh sách phiên đấu giá do user hiện tại tạo.
     */
    public List<Auction> getAuctionsBySellerId(int sellerId) {
        return auctionDAO.getAuctionsBySellerId(sellerId);
    }

    // Các hàm phụ trợ khác (có thể dùng cho chức năng VIEW_ITEMS)
    public List<Auction> getActiveAuctionsList() {
        return List.copyOf(activeAuctions.values());
    }

    /**
     * HOLD BALANCE — Đóng phiên đấu giá với đầy đủ logic thanh toán:
     *
     * Flow:
     *   1. Tìm phiên trong RAM — trả về false nếu không tồn tại.
     *   2. Gọi AuctionDAO.getWinnerInfo() để lấy [winner, bidAmount, seller].
     *   3. Nếu có winner:
     *      a. userDAO.deductBalanceOnWin(winner, bidAmount)
     *         → trừ balance thật, release held_balance (atomic trong 1 SQL)
     *      b. userDAO.updateBalance(seller, bidAmount)
     *         → cộng tiền cho seller
     *      Các bidder thua KHÔNG cần xử lý thêm — held đã được release
     *      từng lần bị vượt giá trong placeBidWithHold().
     *   4. Nếu không có winner (không ai bid): chỉ đổi status → FINISHED.
     *   5. Persist status = 'FINISHED' xuống DB.
     *   6. Xoá khỏi activeAuctions để không còn nhận bid mới.
     *
     * @param auctionId id phiên cần đóng
     * @return true nếu đóng thành công
     */
    public boolean closeAuction(String auctionId) {
        Auction auction = activeAuctions.get(auctionId);
        if (auction == null) {
            System.out.println("[AuctionService] closeAuction: Không tìm thấy phiên " + auctionId);
            return false;
        }

        // Lấy thông tin winner và seller trước khi đóng phiên
        // [0]=winnerUsername, [1]=bidAmount, [2]=sellerUsername
        String[] winnerInfo = auctionDAO.getWinnerInfo(auctionId);

        if (winnerInfo != null) {
            String winner   = winnerInfo[0];
            double bidAmount = Double.parseDouble(winnerInfo[1]);
            String seller   = winnerInfo[2];

            System.out.println("[AuctionService] Phiên " + auctionId + " kết thúc."
                    + " Winner: " + winner + " | Amount: " + bidAmount + " | Seller: " + seller);

            // HOLD BALANCE: Trừ balance thật của winner + release held_balance tương ứng
            // Thực hiện trong 1 SQL atomic: balance -= bidAmount, held_balance -= bidAmount
            boolean deducted = userDAO.deductBalanceOnWin(winner, bidAmount);
            if (!deducted) {
                System.err.println("[AuctionService] Cảnh báo: deductBalanceOnWin thất bại cho " + winner
                        + " — balance có thể không đủ. Vẫn tiếp tục đóng phiên.");
            } else {
                System.out.println("[AuctionService] Đã trừ " + bidAmount + " từ balance của " + winner);
            }

            // Cộng tiền cho seller (bỏ qua nếu seller chính là người thắng — edge case tự bid)
            if (!winner.equals(seller)) {
                boolean credited = userDAO.updateBalance(seller, bidAmount);
                if (credited) {
                    System.out.println("[AuctionService] Đã cộng " + bidAmount + " vào balance của seller " + seller);
                } else {
                    System.err.println("[AuctionService] Cảnh báo: Không thể cộng tiền cho seller: " + seller);
                }
            }
        } else {
            // Không có ai bid — phiên đóng mà không có giao dịch tiền
            System.out.println("[AuctionService] Phiên " + auctionId + " kết thúc — không có ai đặt giá.");
        }

        // Cập nhật trạng thái trên RAM (dùng ReentrantLock bên trong Auction)
        auction.closeAuction();

        // Persist status = 'FINISHED' xuống DB
        boolean dbOk = auctionDAO.closeAuction(auctionId);
        if (!dbOk) {
            System.err.println("[AuctionService] closeAuction: DB không cập nhật được phiên " + auctionId);
        }

        // Xoá khỏi bộ nhớ để không còn nhận bid mới
        activeAuctions.remove(auctionId);

        System.out.println("[AuctionService] Đã đóng phiên đấu giá " + auctionId);
        return true;
    }

    /**
     * Tạo phiên đấu giá mới:
     *   1. Build Auction object đầy đủ (status=OPEN, startTime=now)
     *   2. INSERT xuống DB qua DAO (DAO sẽ set lại auctionId từ generated key)
     *   3. Thêm vào activeAuctions trên RAM ngay lập tức
     */
    public boolean createAuction(int itemId, double startingPrice, String endTime) {
        try {
            // 1. Parse endTime từ String sang LocalDateTime
            LocalDateTime end = LocalDateTime.parse(endTime);
            LocalDateTime start = LocalDateTime.now();

            // 2. Tạo Item tham chiếu (chỉ cần id để ghi xuống DB)
            Item item = new Item();
            item.setId(itemId);

            // 3. Build Auction object — auctionId sẽ được DAO set sau INSERT
            Auction newAuction = new Auction(
                    null,           // auctionId — sẽ được DAO gán sau
                    item,
                    null,           // chưa có leader
                    AuctionStatus.OPEN,
                    startingPrice,
                    start,
                    end,
                    auctionNotifier
            );

            // 4. INSERT vào DB — DAO tự set auctionId từ RETURN_GENERATED_KEYS
            boolean saved = auctionDAO.insertAuction(newAuction);

            if (saved) {
                // 5. Đưa vào RAM cache để client có thể BID ngay
                activeAuctions.put(newAuction.getAuctionId(), newAuction);
                System.out.println("[AuctionService] Phiên đấu giá mới: id="
                        + newAuction.getAuctionId()
                        + " | item=" + itemId
                        + " | giá khởi điểm=" + startingPrice
                        + " | kết thúc=" + end);
                return true;
            }

            System.err.println("[AuctionService] Tạo phiên đấu giá thất bại — DB không lưu được.");
            return false;

        } catch (java.time.format.DateTimeParseException e) {
            System.err.println("[AuctionService] endTime không đúng định dạng ISO (yyyy-MM-ddTHH:mm): " + endTime);
            return false;
        } catch (Exception e) {
            System.err.println("[AuctionService] Lỗi khi tạo phiên đấu giá: " + e.getMessage());
            return false;
        }
    }
}