package com.auction.common.model;

public class Seller extends User {
    private int rating;
    private int totalItemsSold;

    public Seller(int id, String username, String password, int rating, int totalItemsSold) {
        super(id, username, password);
        this.rating = rating;
        this.totalItemsSold = totalItemsSold;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public int getTotalItemsSold() {
        return totalItemsSold;
    }

    public void setTotalItemsSold(int totalItemsSold) {
        this.totalItemsSold = totalItemsSold;
    }
}