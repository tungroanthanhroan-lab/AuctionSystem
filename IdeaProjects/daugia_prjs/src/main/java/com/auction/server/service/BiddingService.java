package com.auction.server.service;

import com.auction.common.model.AutoBid

import java.time.LocalDateTime;
import java.util.ArayList;    
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class BiddingService {
    // bao ve he thong tranh nhieu nguoi bid cung 1ms
    private final ReentrantLock lock = new ReentrantLock();

    // gia lap gia ban san pham, sau se lay tu Database
    private double currentHighestPrice = 10000.0;
    private int currentWinnerId = -1;

    // danh sách các user đăng ký tự động
    private final List<AutoBid> autoBids = new ArrayList<>();

    // hàm đăng ký auto bidding
    public void registerAutoBid(int userId, double maxBid, double increment) {
        lock.lock();
        try {
            System.out.println("\n[SYSTEM] User " + userId + " đăng ký Auto-bid (Max: " + maxBid + ", Bước giá: " + increment + ")");
            // óa auto-bid cũ của user này nếu có để cập nhật cái mới
            autoBids.removeIf(ab -> ab.getUserId() == userId);
            autoBids.add(new AutoBid(userId, maxBid, increment, LocalDateTime.now()));

            // kích hoạt kiểm tra auto-bid ngay sau khi đăng ký
            processAutoBids();
        } finally {
            lock.unlock();
        }
    }

    // ham dat gia
    public boolean placeBid(int userId, double bidAmount) throws Exception {
        lock.lock(); // den sau = doi
        try {
            System.out.println("User " + userId + " đang xử lý đặt giá: " + bidAmount);

            // kiem tra tinh hop le
            if (bidAmount <= currentHighestPrice) {
                System.out.println("-> Thất bại: Giá quá thấp!");
                throw new Exception("Giá đặt phải cao hơn giá hiện hành!");
            }

            // 3. hop le -> cap nhat gia moi
            currentHighestPrice = bidAmount;
            currentWinnerId = userId;
            System.out.println("-> Thành công! Người dẫn đầu mới là User " + userId + " với giá " + bidAmount);

            // sau khi có người đặt thành công, kích hoạt hệ thống autobid của đối thủ
            processAutoBids();
            return true;
        } finally {
            lock.unlock(); // tiep tuc de nguoi khac bid
        }
    }
}

// logic processAutoBids
    private void processAutoBids() {
        boolean isPriceChanged;
        do {
            isPriceChanged = false;
            List<AutoBid> eligibleBids = new ArrayList<>(); // eligibleBids: danh sách những người đủ đk để đấu giá

            // 1. lọc  những người có khả năng tự động trả giá tiếp
            for (AutoBid ab : autoBids) {
                // Không tự đấu giá với chính mình
                if (ab.getUserId() != currentWinnerId) {
                    double nextPrice = currentHighestPrice + ab.getIncrement();
                    
                    // Nếu giá tiếp theo nằm trong giới hạn maxBid
                    if (nextPrice <= ab.getMaxBid()) {
                        eligibleBids.add(ab);
                    } 
                    // Nếu bước giá vượt quá maxBid, nhưng maxBid vẫn cao hơn giá hiện tại -> Nhảy thẳng lên maxBid
                    else if (ab.getMaxBid() > currentHighestPrice) {
                        eligibleBids.add(ab);
                    }
                }
            }

            // 2. nếu có người đủ đk, thực hiện trả giá tự động
            if (!eligibleBids.isEmpty()) {
                // Ưu tiên theo thời điểm đăng ký auto-bid (ai đăng ký trước được xử lý trước)
                eligibleBids.sort(Comparator.comparing(AutoBid::getRegistrationTime)); // dùng sort để săp xếp theo RegisterationTime
                AutoBid nextBidder = eligibleBids.get(0);

                double nextPrice = currentHighestPrice + nextBidder.getIncrement();
                // Đảm bảo không vượt quá mức tối đa
                if (nextPrice > nextBidder.getMaxBid()) {
                    nextPrice = nextBidder.getMaxBid(); 
                }
                // cập nhật trạng thái phiên đấu giá
                currentHighestPrice = nextPrice; 
                currentWinnerId = nextBidder.getUserId();
                
                System.out.println("   [AUTO-BID] Hệ thống tự động trả giá cho User " + currentWinnerId + " -> Lên mức: " + currentHighestPrice);
                
                // Đánh dấu là giá đã thay đổi để vòng lặp tiếp tục kiểm tra xem có ai auto-bid đè lên không
                isPriceChanged = true; 
            }
        } while (isPriceChanged); // Vòng lặp dừng khi không còn auto-bid nào đủ sức chạy tiếp
    }
}
