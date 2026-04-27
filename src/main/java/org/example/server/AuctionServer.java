package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class AuctionServer {

    // Port server lắng nghe kết nối từ client.
    private static final int PORT = 8080;

    public static void main(String[] args) {
        System.out.println("Đang khởi động Server Đấu Giá...");

        // ServerSocket dùng để mở cổng và chờ client kết nối.
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server đã chạy trên cổng " + PORT);

            // Server chạy liên tục để nhận nhiều client.
            while (true) {
                // accept() sẽ đứng chờ cho đến khi có client kết nối.
                Socket clientSocket = serverSocket.accept();

                System.out.println("Có client kết nối: " + clientSocket.getInetAddress());

                // Mỗi client được xử lý ở một thread riêng.
                // Việc này giúp nhiều client có thể đăng nhập/đấu giá cùng lúc.
                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (IOException e) {
            System.err.println("Lỗi Server: Cổng " + PORT + " có thể đang bị chiếm!");
            e.printStackTrace();
        }
    }

    /**
     * Xử lý một client sau khi client kết nối vào server.
     */
    private static void handleClient(Socket clientSocket) {
        // try-with-resources giúp tự đóng socket và stream sau khi xử lý xong.
        try (
                Socket socket = clientSocket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            // Đọc một dòng request từ client.
            String clientMessage = in.readLine();

            System.out.println("Client gửi: " + clientMessage);

            if (clientMessage == null) {
                out.println("ERROR|Yêu cầu rỗng");
                return;
            }

            // Tách request theo dấu |
            // Ví dụ: LOGIN|admin|123456
            // Sau khi split:
            // parts[0] = LOGIN
            // parts[1] = admin
            // parts[2] = 123456
            String[] parts = clientMessage.split("\\|");

            String command = parts[0];

            // Xử lý theo loại lệnh client gửi lên.
            switch (command) {
                case "LOGIN":
                    handleLogin(parts, out);
                    break;

                default:
                    out.println("ERROR|Lệnh không hợp lệ");
                    break;
            }

        } catch (IOException e) {
            System.err.println("Lỗi khi xử lý client");
            e.printStackTrace();
        }
    }

    /**
     * Xử lý yêu cầu đăng nhập.
     *
     * Request hợp lệ có dạng:
     * LOGIN|username|password
     */
    private static void handleLogin(String[] parts, PrintWriter out) {
        // Nếu thiếu username hoặc password thì báo lỗi.
        if (parts.length < 3) {
            out.println("LOGIN_FAILED|Thiếu username hoặc password");
            return;
        }

        String username = parts[1];
        String password = parts[2];

        System.out.println("Đang kiểm tra đăng nhập: " + username);

        /*
         * Tạm thời kiểm tra tài khoản cứng để test luồng client-server.
         * Sau này sẽ thay đoạn này bằng UserDAO để kiểm tra trong database.
         */
        if (username.equals("admin") && password.equals("123456")) {
            out.println("LOGIN_SUCCESS|admin|ADMIN");

        } else if (username.equals("bidder") && password.equals("123456")) {
            out.println("LOGIN_SUCCESS|bidder|BIDDER");

        } else if (username.equals("seller") && password.equals("123456")) {
            out.println("LOGIN_SUCCESS|seller|SELLER");

        } else {
            out.println("LOGIN_FAILED|Sai tài khoản hoặc mật khẩu");
        }
    }
}