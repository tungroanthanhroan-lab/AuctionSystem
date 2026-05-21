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

    /*
     * Lưu lại lệnh login gần nhất.
     *
     * Vì server lưu loggedInUser theo từng socket,
     * nếu socket bị mất rồi reconnect thì client cần login lại tự động.
     */
    private static String lastLoginMessage;

    public static void setLastLoginMessage(String loginMessage) {
        lastLoginMessage = loginMessage;
    }

    private static void connectIfNeeded() throws Exception {
        if (socket == null || socket.isClosed()) {
            socket = new Socket(SERVER_HOST, SERVER_PORT);

            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();

            in = new ObjectInputStream(socket.getInputStream());
        }
    }

    public synchronized String sendMessage(String message) {
        try {
            boolean needAutoLogin = socket == null || socket.isClosed();

            connectIfNeeded();

            /*
             * Nếu socket vừa được mở lại, mà request hiện tại không phải LOGIN,
             * thì tự gửi lại LOGIN trước để server có loggedInUser.
             */
            if (needAutoLogin
                    && lastLoginMessage != null
                    && !lastLoginMessage.trim().isEmpty()
                    && !message.startsWith("LOGIN")) {

                out.writeObject(lastLoginMessage);
                out.flush();

                String loginResponse = readStringResponse();

                System.out.println("Auto login response: " + loginResponse);

                if (!loginResponse.startsWith("SUCCESS")) {
                    return "ERROR|Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại!";
                }
            }

            out.writeObject(message);
            out.flush();

            return readStringResponse();

        } catch (Exception e) {
            e.printStackTrace();
            closeConnectionOnly();
            return "ERROR|Không kết nối được tới server!";
        }
    }

    /*
     * Server có thể gửi object realtime như BidUpdateEvent trước response String.
     * Vì vậy client phải bỏ qua object không phải String,
     * đọc tiếp tới khi nhận được SUCCESS| / FAIL| / ERROR|.
     */
    private static String readStringResponse() throws Exception {
        while (true) {
            Object response = in.readObject();

            if (response instanceof String) {
                return (String) response;
            }

            System.out.println("Nhận object realtime từ server: "
                    + response.getClass().getName());
        }
    }

    /*
     * Đóng socket nhưng KHÔNG xóa lastLoginMessage.
     * Dùng khi lỗi kết nối tạm thời để lần sau có thể auto-login.
     */
    private static void closeConnectionOnly() {
        try {
            if (in != null) {
                in.close();
            }

            if (out != null) {
                out.close();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            in = null;
            out = null;
            socket = null;
        }
    }

    /*
     * Dùng khi logout thật sự.
     * Đóng kết nối và xóa thông tin login đã lưu.
     */
    public static void closeConnection() {
        closeConnectionOnly();
        lastLoginMessage = null;
    }
}