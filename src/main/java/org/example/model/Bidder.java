package org.example.model;

public class Bidder extends User{
    private double balance;

    public Bidder(int id, String username, String password, String role, double balance) {
        super(id, username, password, "Bidder");
        this.balance = balance;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}