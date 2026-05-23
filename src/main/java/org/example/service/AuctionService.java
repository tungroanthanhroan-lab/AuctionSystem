package org.example.service;

import org.example.dao.AuctionDAO;
import org.example.model.Auction;
import org.example.observer.BidUpdateEvent;
import org.example.model.Bidder;
import org.example.observer.AuctionNotifier;

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
    /**
     * Đóng phiên đấu giá thật trên server.
     *
     * Flow:
     * 1. Update status trong DB thành FINISHED.
     * 2. Xóa phiên khỏi activeAuctions trên RAM.
     * 3. VIEW_ITEMS sẽ không còn trả phiên này nữa.
     */
    public boolean closeAuction(String auctionId) {
        try {
            if (auctionId == null || auctionId.trim().isEmpty()) {
                return false;
            }

            boolean dbUpdated = auctionDAO.closeAuction(auctionId);

            if (!dbUpdated) {
                return false;
            }

            /*
             * Xóa khỏi danh sách phiên đang mở trong RAM.
             * Vì VIEW_ITEMS đang lấy từ activeAuctions,
             * nên sau khi đóng phiên, quay lại list sẽ không còn thấy phiên đó.
             */
            activeAuctions.remove(auctionId);

            System.out.println("[AuctionService] Đã đóng phiên đấu giá: " + auctionId);

            return true;

        } catch (Exception e) {
            System.out.println("[AuctionService] Lỗi đóng phiên đấu giá: " + e.getMessage());
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
    // Các hàm phụ trợ khác (có thể dùng cho chức năng VIEW_ITEMS)
    public List<Auction> getActiveAuctionsList() {
        return List.copyOf(activeAuctions.values());
    }
}