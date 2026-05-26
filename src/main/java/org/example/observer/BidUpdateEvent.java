package org.example.observer;

import org.example.model.Bidder;
import java.io.Serializable;

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

    // FIX: trả String (username) thay vì Bidder nguyên bản để AuctionClient dễ dàng in ra màn hình
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