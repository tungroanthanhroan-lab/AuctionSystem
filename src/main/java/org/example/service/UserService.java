package org.example.service;
import org.example.dao.UserDAO;
import org.example.model.User;

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
}
