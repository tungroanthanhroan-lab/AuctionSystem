package org.example.service;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class NetworkService {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;

    private static Socket socket;
    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    private static String lastLoginMessage;

    public static void setLastLoginMessage(String loginMessage) {
        lastLoginMessage = loginMessage;
    }

    // 1. Thêm synchronized vào đây để đảm bảo việc kết nối không bị tạo 2 lần cùng lúc
    private static synchronized void connectIfNeeded() throws Exception {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            // Đóng các stream cũ trước khi tạo mới để dọn dẹp bộ nhớ
            closeConnectionOnly();

            socket = new Socket(SERVER_HOST, SERVER_PORT);
            // Thiết lập timeout để tránh treo máy khi server không phản hồi
            socket.setSoTimeout(5000);

            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();

            in = new ObjectInputStream(socket.getInputStream());
            System.out.println("[Network] Kết nối server thành công.");
        }
    }

    // 2. Hàm sendMessage đã có synchronized là đúng, giữ nguyên để khóa luồng
    public synchronized String sendMessage(String message) {
        try {
            // Kiểm tra xem có cần login lại không trước khi thực hiện kết nối
            boolean needAutoLogin = (socket == null || socket.isClosed());

            connectIfNeeded();

            // Tự động Login lại nếu socket vừa khởi tạo lại
            if (needAutoLogin
                    && lastLoginMessage != null
                    && !lastLoginMessage.trim().isEmpty()
                    && !message.startsWith("LOGIN")) {

                out.writeObject(lastLoginMessage);
                out.flush();
                out.reset(); // Thêm reset để xóa cache ObjectOutputStream

                String loginResponse = readStringResponse();
                System.out.println("Auto login response: " + loginResponse);

                if (!loginResponse.startsWith("SUCCESS")) {
                    return "ERROR|Phiên đăng nhập hết hạn!";
                }
            }

            // Gửi tin nhắn chính
            out.writeObject(message);
            out.flush();
            out.reset(); // Xóa cache để tránh lỗi lặp dữ liệu cũ

            return readStringResponse();

        } catch (Exception e) {
            System.err.println("[Network Error] " + e.getMessage());
            closeConnectionOnly(); // Khi lỗi phải đóng ngay để lần sau connectIfNeeded tạo lại cái mới
            return "ERROR|Không kết nối được tới server!";
        }
    }

    private static String readStringResponse() throws Exception {
        while (true) {
            Object response = in.readObject();
            if (response instanceof String) {
                String res = (String) response;

                System.out.println("Server trả về: " + res);

                // Gom tất cả điều kiện vào trong ngoặc của if
                if (res.startsWith("SUCCESS") ||
                        res.startsWith("FAIL") ||
                        res.startsWith("ERROR") ||
                        res.startsWith("BID_HISTORY") ||
                        res.startsWith("AUCTIONS") ||
                        res.startsWith("BALANCE") ||
                        res.startsWith("MY_AUCTIONS")) { // Đóng ngoặc ở đây

                    return res;
                }
            }
            System.out.println("[Network] Bỏ qua tin nhắn thông báo: " + response);
        }
    }
    private static synchronized void closeConnectionOnly() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
            // Ignore error when closing
        } finally {
            in = null;
            out = null;
            socket = null;
        }
    }

    public static void closeConnection() {
        closeConnectionOnly();
        lastLoginMessage = null;
    }
}