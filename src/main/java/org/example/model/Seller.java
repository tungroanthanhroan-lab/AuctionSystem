package org.example.model;

public class Seller extends User{
    private float rating;

    public Seller(int id, String username, String password, String role, float rating) {
        super(id, username, password, role);
        this.rating = rating;
    }

    public float getRating() {
        return rating;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }
}