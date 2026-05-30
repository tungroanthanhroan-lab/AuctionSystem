package org.example.service;

import org.example.dao.UserDAO;
import org.example.model.User;

/**
 * UserService — user management business layer.
 *
 * MERGE NOTE: Master version kept in full. Rebuild only had login() and register().
 * The additional methods here (getBalance, getHeldBalance, getAvailableBalance,
 * updateBalance, changePassword) are required by ClientHandler for the full command
 * set (CHECK_BALANCE, DEPOSIT, CHANGE_PASSWORD) used by the JavaFX UI.
 */
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

    /** Raw balance (includes held portion). Use getAvailableBalance() for bidding checks. */
    public double getBalance(String username) {
        return userDAO.getBalance(username);
    }

    /** Money currently locked for active bids. */
    public double getHeldBalance(String username) {
        return userDAO.getHeldBalance(username);
    }

    /** Money the user can actually spend (balance - held_balance). */
    public double getAvailableBalance(String username) {
        return userDAO.getAvailableBalance(username);
    }

    /** Deposit / credit money. Also used to pay seller on auction close. */
    public boolean updateBalance(String username, double amount) {
        return userDAO.updateBalance(username, amount);
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        return userDAO.changePassword(username, oldPassword, newPassword);
    }
}