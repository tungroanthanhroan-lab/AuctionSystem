package org.example.observer;

import org.example.model.Bidder;
import java.io.Serializable;

/**
 * Event broadcast to all connected clients when a new bid succeeds.
 */
public class BidUpdateEvent implements Serializable {
    private String auctionId;
    private double newHighestAmount;
    private Bidder bidder;
    private String timestamp;

    public BidUpdateEvent(String auctionId, double newHighestAmount, Bidder bidder, String timestamp) {
        this.auctionId        = (auctionId != null) ? auctionId : "";
        this.newHighestAmount = newHighestAmount;
        this.bidder           = bidder;
        this.timestamp        = timestamp;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public double getNewHighestAmount() {
        return newHighestAmount;
    }

    public void setNewHighestAmount(double newHighestAmount) {
        this.newHighestAmount = newHighestAmount;
    }

    /** Returns the bidder's username — safe for serialization and client-side printing */
    public String getBidder() {
        return bidder != null ? bidder.getUsername() : "";
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