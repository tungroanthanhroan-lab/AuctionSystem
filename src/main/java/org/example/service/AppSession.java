package org.example.service;

public class AppSession {

    /*
     * Lưu username của người đang đăng nhập hiện tại.
     * Đây là dữ liệu tạm ở client để các màn hình khác dùng.
     */
    private static String currentUsername;

    public static void setCurrentUsername(String username) {
        currentUsername = username;
    }

    public static String getCurrentUsername() {
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            return "guest";
        }

        return currentUsername;
    }

    public static void clear() {
        currentUsername = null;
    }
}
//LoginController lưu username vào AppSession
//BiddingController lấy username từ AppSession