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
 * Entry point for the Auction Server.
 */

public class AuctionServer {

    private static final int PORT        = 8080;
    private static final int MAX_THREADS = 100;

    public static void main(String[] args) {
        System.out.println("=== Khởi động Auction Server ===");

        // 1. Init DB schema in correct FK order
        UserDAO userDAO = new UserDAO();
        userDAO.createTableIfNotExists();

        ItemDAO itemDAO = new ItemDAO();
        itemDAO.createTableIfNotExists();

        AuctionDAO auctionDAO = new AuctionDAO();
        auctionDAO.createTable();

        BidDAO bidDAO = new BidDAO();
        bidDAO.createTable();

        // 2. Init services and notifier
        AuctionNotifier notifier   = new AuctionNotifier();
        UserService     userService = new UserService(userDAO);

        // MERGE: pass userDAO for HOLD BALANCE support (rebuild only passed 2 args here)
        AuctionService auctionService = new AuctionService(auctionDAO, notifier, userDAO);

        // 3. Thread pool with limit to protect against DoS
        ExecutorService clientPool = Executors.newFixedThreadPool(MAX_THREADS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Đang tắt server an toàn...");
            clientPool.shutdown();
            notifier.shutdown();
            System.out.println("[Server] Tài nguyên và Thread Pool đã được dọn dẹp.");
        }));

        // 4. Accept connections
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] Đang lắng nghe trên cổng " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] Client mới: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(
                        clientSocket, userService, auctionService, notifier, bidDAO
                );
                clientPool.submit(handler);
            }

        } catch (IOException e) {
            System.err.println("[Server] Lỗi nghiêm trọng: Cổng " + PORT + " không khả dụng!");
            e.printStackTrace();
        }
    }
}