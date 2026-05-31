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
 * FIX BUG 11: Dùng ObjectOutputStream/ObjectInputStream thay vì PrintWriter/BufferedReader
 *             để đồng nhất protocol với server (tránh StreamCorruptedException).
 *
 * MERGE RESOLUTION (Minor — IP input removed in rebuild):
 *  - rebuild: removed the "enter server IP" prompt, hardcoded HOST = "localhost"
 *  - master:  kept the IP input feature so testers can connect to a remote server
 *
 * KEEP: master's IP input feature — useful for a student group project where server
 * and client may run on different machines on the same LAN.
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
            HOST = inputIp;
        }

        System.out.println("Đang kết nối tới " + HOST + ":" + PORT + " ...");

        try (Socket socket = new Socket(HOST, PORT)) {
            System.out.println("Kết nối thành công!\n");

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

            System.out.println("Nhập lệnh (REGISTER|LOGIN|VIEW_ITEMS|BID|CREATE_AUCTION|EXIT):");
            System.out.println("  Ví dụ tạo phiên: CREATE_AUCTION|title|startingPrice|endTime");
            System.out.println("  (endTime dạng ISO: 2025-12-31T23:59)");
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
