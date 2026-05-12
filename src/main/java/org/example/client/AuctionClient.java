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
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Gửi mật lệnh Đăng Ký
            String command1 = "REGISTER|nguoichoi1|654321|USER";

            out.println(command1);
            System.out.println("Tôi đã gửi lệnh: " + command1);

            // Server trả lời
            System.out.println("Kết quả từ Server:");
            String responseLine1;
            while ((responseLine1 = in.readLine()) != null) {
                System.out.println(responseLine1);
            }

            // Gửi chuỗi Đăng Nhập
            String command = "LOGIN|nguoichoi1|654321";

            out.println(command);
            System.out.println("Tôi đã gửi lệnh: " + command);

            // Server trả lời
            System.out.println("Kết quả từ Server:");
            String responseLine;
            while ((responseLine = in.readLine()) != null) {
                System.out.println(responseLine);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}