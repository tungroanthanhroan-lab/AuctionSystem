import com.auction.server.service.AuctionScheduler;
import com.auction.server.service.BiddingService;

public class Main {
    public static void main(String[] args) {
        // 1. Test thử đếm ngược
        AuctionScheduler scheduler = new AuctionScheduler();
        scheduler.scheduleEndAuction(101, 3); // 3 giây sau sẽ tự in ra dòng chốt đơn

        // 2. Test thử bộ đặt giá
        BiddingService service = new BiddingService();
        try {
            service.placeBid(1, 15000); // User 1 đặt 15k -> Sẽ thành công
            service.placeBid(2, 12000); // User 2 đặt 12k -> Sẽ ném ra lỗi Exception
        } catch (Exception e) {
            System.out.println("Lỗi từ server: " + e.getMessage());
        }
    }
}