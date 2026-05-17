package org.example.model;

public class Bidder extends User {
    private double balance;

    // Constructor đầy đủ — dùng khi load từ DB
    public Bidder(int id, String username, String password, String role, double balance) {
        super(id, username, password, role);
        this.balance = balance;
    }

    // Constructor dùng trong AdvancedAuctionTest: (int, String, String, double)
    public Bidder(int id, String username, String password, double balance) {
        super(id, username, password, "BIDDER");
        this.balance = balance;
    }

    // Constructor tiện lợi — dùng khi chỉ có username
    public Bidder(String username) {
        super(0, username, "", "BIDDER");
        this.balance = 0;
    }

    // Constructor tiện lợi — dùng khi có id + username
    public Bidder(int id, String username) {
        super(id, username, "", "BIDDER");
        this.balance = 0;
    }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}
