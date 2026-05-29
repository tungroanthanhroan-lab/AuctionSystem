package org.example.client;

import org.example.observer.BidUpdateEvent;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client đấu giá — kết nối tới AuctionServer qua TCP Socket.
 *
 * FIX BUG 11: Thay PrintWriter/BufferedReader bằng ObjectOutputStream/ObjectInputStream
 *             để đồng nhất protocol với server (tránh StreamCorruptedException).
 */
public class AuctionClient {

    private static String HOST = "localhost";
    private static final int PORT = 8080;

    public static void main(String[] args) {
        System.out.println("=== Auction Client ===");

        Scanner ipScanner = new Scanner(System.in);
        System.out.print("Nhập IP máy chủ (Nhấn Enter để dùng localhost): ");
        String inputIp = ipScanner.nextLine().trim();

        if (!inputIp.isEmpty()) {
            HOST = inputIp; // Ghi đè biến HOST bằng IP vừa nhập
        }

        System.out.println("Đang kết nối tới " + HOST + ":" + PORT + " ...");

        try (Socket socket = new Socket(HOST, PORT)) {
            System.out.println("Kết nối thành công!\n");

            // FIX BUG 11: Dùng ObjectStream — cùng protocol với server
            // LUÔN tạo Output trước Input để tránh Deadlock TCP
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // Luồng nền: lắng nghe thông báo real-time từ server
            ExecutorService listener = Executors.newSingleThreadExecutor();
            listener.submit(() -> {
                try {
                    Object response;
                    while ((response = in.readObject()) != null) {
                        if (response instanceof BidUpdateEvent event) {
                            System.out.println("[🔔 BID UPDATE] Phiên [" + event.getAuctionId()
                                    + "] — " + event.getBidder()
                                    + " đặt giá: " + event.getNewHighestAmount());
                        } else if (response instanceof String msg) {
                            System.out.println("[Server] " + msg);
                        }
                    }
                } catch (EOFException e) {
                    System.out.println("Server đã đóng kết nối.");
                } catch (Exception e) {
                    System.out.println("Mất kết nối: " + e.getMessage());
                }
            });

            // Đọc lệnh từ bàn phím
            System.out.println("Nhập lệnh (REGISTER|LOGIN|VIEW_ITEMS|BID|CREATE_AUCTION|EXIT):");
            System.out.println("  Ví dụ tạo phiên: CREATE_AUCTION|itemId|startingPrice|endTime");
            System.out.println("  (endTime dạng ISO: 2025-12-31T23:59, chỉ ADMIN mới dùng được)");
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;
                if ("EXIT".equalsIgnoreCase(input)) break;
                out.writeObject(input);
                out.flush();
            }

            listener.shutdown();

        } catch (Exception e) {
            System.err.println("[Client] Lỗi kết nối: " + e.getMessage());
            e.printStackTrace();
        }
    }
}