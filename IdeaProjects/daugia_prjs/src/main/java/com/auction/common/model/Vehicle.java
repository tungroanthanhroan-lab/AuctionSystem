package com.auction.common.model;

public class Vehicle extends Item{
    private int mileage;

    public Vehicle(int id, String name, double startingPrice, int mileage) {
        super(id, name, startingPrice);
        this.mileage = mileage;
    }

    public int getMileage() {
        return mileage;
    }
}
