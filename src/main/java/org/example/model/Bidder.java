package org.example.model;

public class Bidder extends User {
    private double balance;

    /** Full constructor — used when loading from DB */
    public Bidder(int id, String username, String password, String role, double balance) {
        super(id, username, password, role);
        this.balance = balance;
    }

    /**
     * Constructor used by AdvancedAuctionTest: (int id, String username, String password, double balance).
     * MERGE NOTE: Added by rebuild to support the test file — master only had 5-arg constructor.
     */
    public Bidder(int id, String username, String password, double balance) {
        super(id, username, password, "BIDDER");
        this.balance = balance;
    }

    /** Convenience constructor — used in AuctionService / ClientHandler when only username is known */
    public Bidder(String username) {
        super(0, username, "", "BIDDER");
        this.balance = 0;
    }

    /** Convenience constructor — used when only id + username are available */
    public Bidder(int id, String username) {
        super(id, username, "", "BIDDER");
        this.balance = 0;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}