package org.example.model;

public class Bidder extends User{
    private double balance;

    public Bidder(String id, String username, String password, double balance) {
        super(id, username, password, "Bidder");
        this.balance = balance;
    }

}