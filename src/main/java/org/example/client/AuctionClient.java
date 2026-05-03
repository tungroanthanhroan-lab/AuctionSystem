package org.example.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class AuctionClient {
    public static void main(String[] args) {
        System.out.println("Đang tìm đường đến Server...");

        // Tìm đến máy có địa chỉ "localhost" với port là 8080
        try (Socket socket = new Socket("localhost", 8080)) {
            System.out.println("Đã kết nối thành công với Server!");

            // Hai đối tượng để tạo yêu cầu và nhận thông tin từ server
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Gửi tin cho Server
            String myMessage = "Cho tôi đăng nhập với user: admin, pass: 123456";
            out.println(myMessage);
            System.out.println("Tôi đã gửi: " + myMessage);

            // Server phản hồi
            String serverReply = in.readLine();
            System.out.println("Server trả lời: " + serverReply);

        } catch (IOException e) {
            System.err.println("Không tìm thấy Server! Chắc chưa bật Server.");
        }
    }
}