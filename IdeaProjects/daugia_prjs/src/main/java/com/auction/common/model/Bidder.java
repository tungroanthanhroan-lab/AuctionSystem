package com.auction.common.model;

public class Bidder extends User{
    private double balance;

    public Bidder(int id, String username, String password, double balance) {
        super(id, username, password);
        this.balance = balance;
    }

}
