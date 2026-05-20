package org.example.service;

public class AppSession {

    /*
     * Lưu username của tài khoản đang đăng nhập hiện tại.
     * Các controller khác có thể dùng để biết ai đang thao tác.
     */
    private static String currentUsername;

    /*
     * Lấy username đang đăng nhập.
     */
    public static String getCurrentUsername() {
        return currentUsername;
    }

    /*
     * Lưu username sau khi đăng nhập thành công.
     * Khi đăng xuất có thể set null.
     */
    public static void setCurrentUsername(String username) {
        currentUsername = username;
    }

    /*
     * Xóa thông tin đăng nhập khi logout.
     */
    public static void clear() {
        currentUsername = null;
    }
}