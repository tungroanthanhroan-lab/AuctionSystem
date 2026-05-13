package org.example.exception;
// tu tao loi
// cac loi logic

public class AuctionClosedException extends RuntimeException {
    public AuctionClosedException(String msg) {
        super(msg);
    }
}