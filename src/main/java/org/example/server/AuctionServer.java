package org.example.server;

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

                // 2 Hàm để nhận thông tin và trả lời
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Nhận yêu cầu
                String clientMessage = in.readLine();
                System.out.println("Khách hàng yêu cầu: " + clientMessage);

                // Nhận yêu cầu thành công
                out.println("Chào Bạn! Hệ thống đã nhận được yêu cầu: [" + clientMessage + "]. Chờ chút để báo Database nhé!");

                // Đóng kết nối để quay lại cửa đón người khác
                clientSocket.close();
                System.out.println("Đã phục vụ xong 1 khách.");
                System.out.println();
            }

        } catch (IOException e) {
            System.err.println("Lỗi Server: Cổng " + PORT + " có thể đã bị phần mềm khác chiếm dụng!");
            e.printStackTrace();
        }
    }
}