package com.auction.common.model;

public class Electronics extends Item{
    private int warrantyMonths; // thang bao hanh

    public Electronics(int id, String name, double startingPrice, int warrantyMonths) {
        super(id, name, startingPrice); // goi lai constructor cua lop cha
        this.warrantyMonths = warrantyMonths;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }
}
