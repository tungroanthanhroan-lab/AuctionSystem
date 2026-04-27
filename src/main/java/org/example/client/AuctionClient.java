package org.example.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class AuctionClient {

    // Địa chỉ server. Vì chạy cùng máy nên dùng localhost.
    private static final String SERVER_HOST = "localhost";

    // Port phải trùng với port bên AuctionServer.
    private static final int SERVER_PORT = 8080;

    /**
     * Gửi yêu cầu đăng nhập từ client lên server.
     *
     * @param username tài khoản người dùng nhập ở giao diện
     * @param password mật khẩu người dùng nhập ở giao diện
     * @return phản hồi từ server, ví dụ:
     *         LOGIN_SUCCESS|admin|ADMIN
     *         LOGIN_FAILED|Sai tài khoản hoặc mật khẩu
     *         ERROR|Không kết nối được tới server
     */
    public String login(String username, String password) {
        // try-with-resources giúp tự động đóng socket, input, output sau khi xử lý xong.
        try (
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // Quy ước gói tin gửi cho server:
            // LOGIN|username|password
            String request = "LOGIN|" + username + "|" + password;

            // Gửi request sang server.
            out.println(request);

            // Chờ server trả lời một dòng kết quả.
            return in.readLine();

        } catch (IOException e) {
            // Nếu server chưa bật hoặc mất kết nối thì trả về lỗi cho LoginController xử lý popup.
            return "ERROR|Không kết nối được tới server";
        }
    }
    public static void main(String[] args) {
        AuctionClient client = new AuctionClient();

        String response = client.login("abc", "123");

        System.out.println("Server trả về: " + response);
    }
}