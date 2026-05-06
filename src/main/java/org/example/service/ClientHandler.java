package org.example.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // Nhận dữ liệu từ client
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );

            // Gửi phản hồi dạng text về client
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Nhận yêu cầu từ client
            String command = in.readLine();
            System.out.println("Khách hàng yêu cầu: " + command);

            if ("VIEW_ITEMS".equals(command)) {
                // Chức năng này để xử lý sau
                out.println("FAIL|Chuc nang VIEW_ITEMS chua duoc xu ly");

            } else if (command != null && command.startsWith("LOGIN")) {
                // Client gửi: LOGIN|username|password
                String[] parts = command.split("\\|");

                if (parts.length == 3) {
                    String username = parts[1];
                    String password = parts[2];

                    org.example.dao.UserDAO userDAO = new org.example.dao.UserDAO();

                    // Gọi DAO để kiểm tra tài khoản trong database
                    org.example.model.User loggedInUser = userDAO.login(username, password);

                    if (loggedInUser != null) {
                        out.println("SUCCESS|Đăng nhập thành công, chào mừng! "
                                + loggedInUser.getRole() + " " + loggedInUser.getUsername());
                    } else {
                        out.println("FAIL|Sai tài khoản hoặc mật khẩu!");
                    }

                } else {
                    out.println("FAIL|Sai cú pháp đăng nhập. Mẫu chuẩn: LOGIN|user|pass");
                }

            } else if (command != null && command.startsWith("REGISTER")) {
                // Client gửi: REGISTER|username|password|role
                String[] parts = command.split("\\|");

                if (parts.length == 4) {
                    String username = parts[1];
                    String password = parts[2];
                    String role = parts[3];

                    org.example.dao.UserDAO userDAO = new org.example.dao.UserDAO();

                    // Gọi DAO để lưu tài khoản mới vào database
                    boolean isRegistered = userDAO.registerUser(username, password, role);

                    if (isRegistered) {
                        out.println("SUCCESS|Đã tạo thành công tài khoản: " + username);
                    } else {
                        out.println("FAIL|Tên đăng nhập này đã tồn tại!");
                    }

                } else {
                    out.println("FAIL|Sai cú pháp đăng kí. Mẫu chuẩn: REGISTER|user|pass|role");
                }

            } else if (command != null && command.startsWith("BID")) {
                // Client gửi: BID|username|price
                String[] parts = command.split("\\|");

                if (parts.length == 3) {
                    String username = parts[1];

                    try {
                        double amount = Double.parseDouble(parts[2]);

                        System.out.println("User " + username + " đặt giá: " + amount);

                        /*
                         * Tạm thời server chỉ xác nhận đã nhận BID.
                         * Giá, người dẫn đầu và lịch sử bid hiện vẫn được UI lưu tạm
                         * bằng AuctionDataStore.
                         */
                        out.println("SUCCESS|Đã nhận giá đặt của " + username + ": " + amount + "$");

                    } catch (NumberFormatException e) {
                        out.println("FAIL|Gía dặt không hợp lệ");
                    }

                } else {
                    out.println("FAIL|Sai cú pháp đặt giá. Mẫu chuẩn: BID|user|price");
                }

            } else {
                out.println("FAIL|Server không hiểu lệnh này!");
            }

            // Đóng kết nối sau khi xử lý xong một request
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}