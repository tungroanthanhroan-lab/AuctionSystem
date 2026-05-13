package org.example.observer;


import org.example.model.Auction;
import org.example.model.Bidder;

import java.io.Serializable;

public class BidUpdateEvent implements Serializable {
    private Auction auction;
    private double newHighestAmount;
    private Bidder bidder;
    private String timestamp;

    public BidUpdateEvent(Auction auction, double newHighestAmount, Bidder bidder, String timestamp) {
        this.auction = auction;
        this.newHighestAmount = newHighestAmount;
        this.bidder = bidder;
        this.timestamp = timestamp;
    }

    public String getAuctionId() {
        return auction.getAuctionId();
    }

    public void setAuction(Auction auction) {
        this.auction = auction;
    }

    public double getNewHighestAmount() {
        return newHighestAmount;
    }

    public void setNewHighestAmount(double newHighestAmount) {
        this.newHighestAmount = newHighestAmount;
    }

    public String getBidder() {
        return bidder.getUsername();
    }

    public void setBidder(Bidder bidder) {
        this.bidder = bidder;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
