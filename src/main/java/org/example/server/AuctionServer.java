package org.example.server;

import org.example.dao.AuctionDAO;
import org.example.dao.BidDAO;
import org.example.dao.ItemDAO;
import org.example.dao.UserDAO;
import org.example.observer.AuctionNotifier;
import org.example.service.AuctionService;
import org.example.service.ClientHandler;
import org.example.service.UserService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point của Auction Server.
 * * Đã resolve conflict thành công giữa nhánh allcode và rebuild-models:
 * - Đảm bảo nạp đúng thứ tự DB schema trơn tru.
 * - Inject chuẩn 5 tham số cho ClientHandler tương thích giao thức ObjectStream.
 * - Giới hạn ThreadPool tối đa bảo vệ tài nguyên hệ thống kèm Shutdown Hook dọn dẹp bộ nhớ.
 */
public class AuctionServer {

    private static final int PORT = 8080;
    // Giới hạn tối đa 100 client đồng thời để bảo vệ tài nguyên hệ thống
    private static final int MAX_THREADS = 100;

    public static void main(String[] args) {
        System.out.println("=== Khởi động Auction Server ===");

        // 1. ── Khởi tạo DB schema theo đúng thứ tự liên kết Khóa ngoại ──
        UserDAO userDAO = new UserDAO();
        userDAO.createTableIfNotExists();

        ItemDAO itemDAO = new ItemDAO();
        itemDAO.createTableIfNotExists();

        AuctionDAO auctionDAO = new AuctionDAO();
        auctionDAO.createTable();

        BidDAO bidDAO = new BidDAO();
        bidDAO.createTable();

        // 2. ── Khởi tạo các thành phần điều hướng & thông báo (Services / Notifier) ──
        AuctionNotifier notifier = new AuctionNotifier();
        UserService userService = new UserService(userDAO);
        AuctionService auctionService = new AuctionService(auctionDAO, notifier);

        // [TÙY CHỌN] Nếu AuctionService của bạn có hàm load dữ liệu cũ từ DB lên RAM khi khởi động:
        // auctionService.loadActiveAuctionsFromDB();

        // 3. ── Quản lý Thread hiệu năng cao có giới hạn chống DoS ──
        ExecutorService clientPool = Executors.newFixedThreadPool(MAX_THREADS);

        // Shutdown hook: Đảm bảo giải phóng port và đóng kết nối an toàn khi tắt ứng dụng
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Đang tiến hành tắt server an toàn...");
            clientPool.shutdown();
            notifier.shutdown();
            System.out.println("[Server] Toàn bộ tài nguyên và Thread Pool đã được dọn dẹp sạch.");
        }));

        // 4. ── Khởi chạy socket lắng nghe kết nối từ các Client ──
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] Đang lắng nghe kết nối trên cổng " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] Client mới kết nối từ IP: " + clientSocket.getInetAddress());

                // Khởi tạo ClientHandler truyền chuẩn xác 5 tham số theo thiết kế mới của hệ thống
                ClientHandler handler = new ClientHandler(
                        clientSocket,
                        userService,
                        auctionService,
                        notifier,
                        bidDAO
                );

                // Submit tác vụ vào pool quản lý thay vì tạo thread thủ công
                clientPool.submit(handler);
            }

        } catch (IOException e) {
            System.err.println("[Server] Lỗi nghiêm trọng: Cổng " + PORT + " có thể đã bị chiếm dụng hoặc không khả dụng!");
            e.printStackTrace();
        }
    }
}
