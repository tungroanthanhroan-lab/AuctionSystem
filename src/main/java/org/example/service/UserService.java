package org.example.service;

import org.example.dao.UserDAO;
import org.example.model.User;

public class UserService {

    private final UserDAO userDAO;

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public User login(String username, String password) {
        return userDAO.login(username, password);
    }

    public boolean register(String username, String password, String role) {
        return userDAO.registerUser(username, password, role);
    }

    /** Số dư tổng/thô (bao gồm cả phần đang bị tạm giữ). Hãy dùng getAvailableBalance() để kiểm tra khi đặt giá (bid). */
    public double getBalance(String username) {
        return userDAO.getBalance(username);
    }

    /** Số tiền hiện đang bị khóa/tạm giữ cho các lượt đặt giá đang diễn ra. */
    public double getHeldBalance(String username) {
        return userDAO.getHeldBalance(username);
    }

    /** Số tiền thực tế người dùng có thể chi tiêu (số dư tổng - số tiền tạm giữ). */
    public double getAvailableBalance(String username) {
        return userDAO.getAvailableBalance(username);
    }

    /** Nạp tiền / cộng tiền vào tài khoản. Đồng thời cũng được dùng để thanh toán cho người bán khi phiên đấu giá kết thúc. */
    public boolean updateBalance(String username, double amount) {
        return userDAO.updateBalance(username, amount);
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        return userDAO.changePassword(username, oldPassword, newPassword);
    }
}