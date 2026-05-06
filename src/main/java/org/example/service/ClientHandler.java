package org.example.service;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable, AuctionObserve{
    private Socket socket;
    private ObjectOutputStream out;

    public ClientHandler(Socket socket, ObjectOutputStream out) {
        this.socket = socket;
        this.out = out;
    }

    @Override
    public void onBidUpdate(BidUpdateEvent event) {
        try {
            //dua event payload truc tiep qua mang cho frontend
            out.writeObject(event);
            out.flush();
        } catch (Exception e) {
            System.out.println("Khong the gui ket noi cho client nay");
        }
    }
    @Override
    public void run() {
        try {
            // 2 Hàm để nhận thông tin và trả lời
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Nhận yêu cầu
            String command = in.readLine();
            System.out.println("Khách hàng yêu cầu: " + command);

            // Nhận yêu cầu thành công
            out.println("Chào Bạn! Hệ thống đã nhận được yêu cầu: [" + command + "]. Chờ chút để báo Database nhé!");

            if ("VIEW_ITEMS".equals(command)) {
                // Xem lại những gì hệ thống được nhập vào

            } else if (command != null && command.startsWith("LOGIN")) {
                // Người dùng gửi: LOGIN|tên_tài_khoản|mật_khẩu
                String[] parts = command.split("\\|"); // Tách chuỗi dựa vào dấu |

                if (parts.length == 3) {
                    String username = parts[1];
                    String password = parts[2];

                    org.example.dao.UserDAO userDAO = new org.example.dao.UserDAO();

                    // Gọi hàm login, trả về 1 đối tượng User (hoặc null nếu sai pass)
                    org.example.model.User loggedInUser = userDAO.login(username, password);

                    if (loggedInUser != null) {
                        out.println("SUCCESS|Đăng nhập thành công! Chào mừng " + loggedInUser.getRole() + " " + loggedInUser.getUsername());
                    } else {
                        out.println("FAIL|Sai tài khoản hoặc mật khẩu rồi nha!");
                    }
                } else {
                    out.println("FAIL|Sai cú pháp đăng nhập. Mẫu chuẩn: LOGIN|user|pass");
                }
            } else if (command != null && command.startsWith("REGISTER")) {
                String[] parts = command.split("\\|");

                // Kiểm tra xem khách có gửi đủ 4 phần (REGISTER, user, pass, role) không
                if (parts.length == 4) {
                    String username = parts[1];
                    String password = parts[2];
                    String role = parts[3];

                    org.example.dao.UserDAO userDAO = new org.example.dao.UserDAO();

                    // Gọi hàm đăng ký có sẵn của ông
                    boolean isRegistered = userDAO.registerUser(username, password, role);

                    if (isRegistered) {
                        out.println("SUCCESS|Tuyệt vời! Đã tạo thành công tài khoản: " + username);
                    } else {
                        out.println("FAIL|Tên đăng nhập này đã có người xài, vui lòng chọn tên khác!");
                    }
                } else {
                    out.println("FAIL|Sai cú pháp đăng ký. Mẫu chuẩn: REGISTER|user|pass|role");
                }
            } else {
                out.println("Server không hiểu lệnh này!");
            }

            // Đóng kết nối để quay lại cửa đón người khác
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
