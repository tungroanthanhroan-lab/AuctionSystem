package org.example.server;

import org.example.service.ClientHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class AuctionServer {
    // Mở cổng port số 8080
    private static final int PORT = 8080;

    public static void main(String[] args) {
        System.out.println("Đang khởi động Server Đấu Giá...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server đã chạy trên cổng " + PORT + ". Server đang chờ người dùng kết nối...");

            // Vòng lặp vô hạn (while true) để server làm việc 24/24
            while (true) {
                // Lệnh accept() này sẽ "đứng hình" chờ ở đây cho đến khi có 1 Client kết nối tới
                Socket clientSocket = serverSocket.accept();

                System.out.println("Ting ting! Có một khách hàng vừa kết nối: " + clientSocket.getInetAddress());


                ClientHandler handler = new ClientHandler(clientSocket);

                // Đẩy ra luồng riêng để chạy song song
                Thread thread = new Thread(handler);
                thread.start();

                // Server lập tức quay lại bước 1 chờ người dùng mới
            }
        } catch (IOException e) {
            System.err.println("Lỗi Server: Cổng " + PORT + " có thể đã bị phần mềm khác chiếm dụng!");
            e.printStackTrace();
        }
    }
}