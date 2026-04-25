package service;

import java.time.LocalDateTime;

public class BidTransaction {
    private Bidder bidder;
    private double bidAmount;
    private LocalDateTime time;

    public BidTransaction(Bidder bidder, double bidAmount, LocalDateTime time) {
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.time = time;
    }
}
