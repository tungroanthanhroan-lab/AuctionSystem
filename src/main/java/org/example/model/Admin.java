package org.example.model;

public class Admin extends User {
    private int adminLevel;

    public Admin(int id, String username, String password, String role, int adminLevel) {
        super(id, username, password, role);
        this.adminLevel = adminLevel;
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(int adminLevel) {
        this.adminLevel = adminLevel;
    }
}