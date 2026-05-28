package org.example.service;

import org.example.dao.UserDAO;
import org.example.model.User;

/**
 * UserService — tầng service quản lý người dùng.
 *
 * HOLD BALANCE: Bổ sung các phương thức delegate để lớp ngoài (ClientHandler, AuctionService)
 * có thể truy vấn và thao tác held_balance / available_balance mà không cần truy cập DAO trực tiếp.
 */
public class UserService {
    private UserDAO userDAO;

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public User login(String username, String password) {
        return userDAO.login(username, password);
    }

    public boolean register(String username, String password, String role) {
        return userDAO.registerUser(username, password, role);
    }

    /**
     * Lấy số dư thực tế (balance gốc) của user.
     * Lưu ý: đây KHÔNG phải available_balance.
     * Dùng getAvailableBalance() để biết user có thể bid bao nhiêu.
     */
    public double getBalance(String username) {
        return userDAO.getBalance(username);
    }

    /**
     * HOLD BALANCE: Lấy số tiền đang bị đóng băng (held_balance).
     */
    public double getHeldBalance(String username) {
        return userDAO.getHeldBalance(username);
    }

    /**
     * HOLD BALANCE: Lấy available_balance = balance - held_balance.
     * Đây là số tiền user thực sự có thể dùng để bid.
     */
    public double getAvailableBalance(String username) {
        return userDAO.getAvailableBalance(username);
    }

    /**
     * Nạp tiền vào tài khoản.
     * Cũng dùng để cộng tiền cho seller khi phiên kết thúc.
     */
    public boolean updateBalance(String username, double amount) {
        return userDAO.updateBalance(username, amount);
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        return userDAO.changePassword(username, oldPassword, newPassword);
    }
}
