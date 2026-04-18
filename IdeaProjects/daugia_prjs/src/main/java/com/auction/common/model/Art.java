package com.auction.common.model;

public class Art extends Item{
    private String author;

    public Art(int id, String name, double startingPrice, String author) {
        super(id, name, startingPrice);
        this.author = author;
    }

    public String getAuthor() {
        return author;
    }
}
