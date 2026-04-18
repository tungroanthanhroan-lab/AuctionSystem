package com.auction.common.model;

public class Item {
    protected int id;
    protected String name;
    protected double startingPrice;
    //protected de lop con truy cap

    public Item(int id, String name, double startingPrice) {
        this.id = id;
        this.name = name;
        this.startingPrice = startingPrice;
    }

    public int getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public double getStartingPrice() {
        return startingPrice;
    }
}
