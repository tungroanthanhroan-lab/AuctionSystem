package org.example.service;

import org.example.dao.AuctionDAO;
import org.example.model.Auction;
import org.example.model.AuctionStatus;
import org.example.model.Item;
import org.example.observer.BidUpdateEvent;
import org.example.model.Bidder;
import org.example.observer.AuctionNotifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionService {
    private AuctionDAO auctionDAO;
    private AuctionNotifier auctionNotifier;

    // Bộ nhớ Cache lưu trữ các phiên đấu giá đang diễn ra trên RAM
    // Dùng ConcurrentHashMap để Thread-Safe (an toàn khi nhiều luồng truy cập)
    private ConcurrentHashMap<String, Auction> activeAuctions;

    // Inject DAO và Notifier vào qua Constructor
    public AuctionService(AuctionDAO auctionDAO, AuctionNotifier auctionNotifier) {
        this.auctionDAO = auctionDAO;
        this.auctionNotifier = auctionNotifier;
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
     * Logic đặt giá cốt lõi (Xử lý đồng thời cực kỳ nghiêm ngặt)
     */
    public boolean placeBid(String auctionId, String bidderName, double amount) {
        // 1. Lấy phiên đấu giá từ bộ nhớ RAM
        Auction auction = activeAuctions.get(auctionId);

        if (auction == null) {
            System.out.println("Phiên đấu giá không tồn tại hoặc đã đóng.");
            return false;
        }

        // 2. KHÓA ĐỐI TƯỢNG (Locking): Đảm bảo tại 1 thời điểm, chỉ 1 người được xét duyệt giá cho món hàng này
        synchronized (auction) {
            try {
                // 3. Kiểm tra logic nghiệp vụ: Giá mới phải lớn hơn giá hiện tại
                if (amount <= auction.getCurrentHighestBid()) {
                    return false; // Trả về false ngay, đỡ mất công gọi DB
                }

                // 4. Gọi DB để lưu (Sử dụng Optimistic Locking qua cột version)
                // DAO sẽ chạy câu lệnh: UPDATE auction SET price = ?, version = version + 1 WHERE id = ? AND version = ?
                boolean isDbUpdated = auctionDAO.updateBidWithOptimisticLock(auctionId, bidderName, amount, auction.getVersion());

                if (isDbUpdated) {
                    // 5. Nếu DB cập nhật thành công -> Cập nhật RAM
                    auction.setCurrentHighestBid(amount);
                    // Lưu ý: Cần tạo 1 User/Bidder object giả lập hoặc lấy từ DB, ở đây ta tạo tượng trưng
                    auction.setCurrentLeader(new Bidder(bidderName));
                    auction.setVersion(auction.getVersion() + 1); // Tăng version trên RAM cho khớp DB

                    // 6. PHÁT LOA THÔNG BÁO REAL-TIME
                    BidUpdateEvent event = new BidUpdateEvent(
                            auction,
                            amount,
                            auction.getCurrentLeader(),
                            String.valueOf(System.currentTimeMillis())
                    );
                    auctionNotifier.broadcast(event);

                    return true; // Đặt giá thành công mĩ mãn
                } else {
                    // DB báo false nghĩa là có người khác đã nhanh tay update DB trước 1 mili-giây
                    System.out.println("Lỗi đồng thời (Conflict): Dữ liệu DB đã bị thay đổi bởi người khác.");
                    return false;
                }

            } catch (Exception e) {
                System.out.println("Lỗi hệ thống khi đặt giá: " + e.getMessage());
                return false;
            }
        }
    }
    /**
     * Seller/Admin tạo phiên đấu giá mới bằng tên sản phẩm.
     *
     * Flow:
     * 1. Validate dữ liệu.
     * 2. Gọi AuctionDAO để tạo item + auction trong DB.
     * 3. Đưa auction mới vào activeAuctions trên RAM.
     * 4. VIEW_ITEMS sẽ thấy phiên mới ngay.
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
    // Các hàm phụ trợ khác (có thể dùng cho chức năng VIEW_ITEMS)
    public List<Auction> getActiveAuctionsList() {
        return List.copyOf(activeAuctions.values());
    }

    /**
     * Đóng một phiên đấu giá:
     *   1. Tìm phiên trong RAM — trả về false nếu không tồn tại hoặc đã FINISHED.
     *   2. Gọi Auction.closeAuction() để cập nhật trạng thái trên RAM (thread-safe qua ReentrantLock).
     *   3. Gọi AuctionDAO.closeAuction() để persist status = 'FINISHED' xuống DB.
     *   4. Xoá khỏi activeAuctions để VIEW_ITEMS không còn trả về phiên này.
     *
     * @param auctionId id phiên cần đóng (String)
     * @return true nếu đóng thành công
     */
    public boolean closeAuction(String auctionId) {
        Auction auction = activeAuctions.get(auctionId);
        if (auction == null) {
            System.out.println("[AuctionService] closeAuction: Không tìm thấy phiên " + auctionId);
            return false;
        }

        // Cập nhật trạng thái trên RAM (dùng ReentrantLock bên trong)
        auction.closeAuction();

        // Persist xuống DB
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
     *
     * Format lệnh từ client: CREATE_AUCTION|itemId|startingPrice|endTime
     *   - itemId        : id của item trong bảng items
     *   - startingPrice : giá khởi điểm (double)
     *   - endTime       : thời điểm kết thúc dạng ISO (yyyy-MM-ddTHH:mm)
     *
     * @return true nếu tạo thành công
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