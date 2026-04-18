package com.auction.common.model;

public class Admin extends User{
    private String adminLevel;

    public Admin(int id, String username, String password, String adminLevel) {
        super(id, username, password);
        this.adminLevel = adminLevel;
    }

    public String getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(String adminLevel) {
        this.adminLevel = adminLevel;
    }
}
