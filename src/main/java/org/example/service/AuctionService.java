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

    // Các hàm phụ trợ khác (có thể dùng cho chức năng VIEW_ITEMS)
    public List<Auction> getActiveAuctionsList() {
        return List.copyOf(activeAuctions.values());
    }
}