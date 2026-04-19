import com.auction.server.service.AuctionScheduler;
import com.auction.server.service.BiddingService;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== 1. TEST BỘ ĐẾM NGƯỢC ===");
        AuctionScheduler scheduler = new AuctionScheduler();
        // 3 giây sau sẽ tự in ra dòng chốt đơn
        scheduler.scheduleEndAuction(101, 3); 

        System.out.println("\n=== 2. TEST CHỨC NĂNG AUTO-BIDDING ===");
        BiddingService service = new BiddingService();

        try {
            // Giá khởi điểm hệ thống đang là 10000
            
            // Kịch bản 1: User 1 đăng ký auto-bid, giá max 15000, bước giá 500
            service.registerAutoBid(1, 15000, 500);

            // Kịch bản 2: User 2 nhảy vào đặt tay 11000
            // Hệ thống sẽ ghi nhận User 2, nhưng ngay lập tức Auto-bid của User 1 sẽ bật lên để giành lại top
            service.placeBid(2, 11000);

            // Kịch bản 3: User 3 đăng ký auto-bid max 18000, bước giá 1000
            // Lúc này User 1 và User 3 sẽ tự động đấu với nhau lặp đi lặp lại 
            // cho đến khi User 1 chạm ngưỡng 15000 và chịu thua
            service.registerAutoBid(3, 18000, 1000);

        } catch (Exception e) {
            System.out.println("Lỗi từ server: " + e.getMessage());
        }
    }
}
