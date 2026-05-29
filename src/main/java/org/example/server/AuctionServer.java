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
 *
 * FIX BUG 4: Khởi tạo đầy đủ dependencies và truyền vào ClientHandler (4 tham số).
 * FIX BUG 10: Dùng ExecutorService (CachedThreadPool) thay vì new Thread() thủ công.
 */
public class AuctionServer {

    private static final int PORT = 8080;
    // Giới hạn tối đa 100 client đồng thời để bảo vệ tài nguyên
    private static final int MAX_THREADS = 100;

    public static void main(String[] args) {
        System.out.println("=== Khởi động Auction Server ===");

        // ── Khởi tạo DB schema ──
        UserDAO userDAO = new UserDAO();
        userDAO.createTableIfNotExists();

        ItemDAO itemDAO = new ItemDAO();
        itemDAO.createTableIfNotExists();

        AuctionDAO auctionDAO = new AuctionDAO();
        auctionDAO.createTable();

        BidDAO bidDAO = new BidDAO();
        bidDAO.createTable();

        // ── Khởi tạo Services ──
        AuctionNotifier notifier = new AuctionNotifier();
        UserService userService = new UserService(userDAO);
        // HOLD BALANCE: Truyền thêm userDAO vào AuctionService để xử lý hold/release/deduct tiền
        AuctionService auctionService = new AuctionService(auctionDAO, notifier, userDAO);

        // FIX BUG 10: ThreadPool có giới hạn, không tạo thread vô hạn
        ExecutorService clientPool = Executors.newFixedThreadPool(MAX_THREADS);

        // Shutdown hook: dọn dẹp khi server bị tắt
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Đang tắt server...");
            clientPool.shutdown();
            notifier.shutdown();
            System.out.println("[Server] Server đã tắt sạch.");
        }));

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] Đang lắng nghe trên cổng " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] Client mới kết nối: " + clientSocket.getInetAddress());

                // FIX BUG 7: Truyền đủ 5 tham số vào ClientHandler constructor
                ClientHandler handler = new ClientHandler(
                        clientSocket,
                        userService,
                        auctionService,
                        notifier,
                        bidDAO
                );

                // FIX BUG 10: Submit vào pool thay vì new Thread().start()
                clientPool.submit(handler);
            }

        } catch (IOException e) {
            System.err.println("[Server] Lỗi: Cổng " + PORT + " có thể đã bị chiếm dụng!");
            e.printStackTrace();
        }
    }
}