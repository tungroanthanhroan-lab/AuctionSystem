package org.example;

import org.example.dao.UserDAO;
import org.example.model.User;

public class Main {
    public static void main(String[] args) {
        UserDAO userDAO = new UserDAO();

        // 1. Tạo bảng (nếu chưa có)
        userDAO.createTableIfNotExists();

        // 2. Đăng ký thử 1 user
        boolean isRegistered = userDAO.registerUser("admin", "123456", "ADMIN");
        if (isRegistered) System.out.println("Đăng ký thành công!");

        // 3. Đăng nhập thử
        User user = userDAO.login("admin", "123456");
        if (user != null) {
            System.out.println("Chào mừng: " + user.getUsername() + " với quyền: " + user.getRole());
        } else {
            System.out.println("Sai tài khoản hoặc mật khẩu!");
        }
    }
}