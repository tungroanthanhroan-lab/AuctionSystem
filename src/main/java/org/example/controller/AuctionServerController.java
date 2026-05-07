package org.example.controller;

import org.example.exception.AuctionClosedException;
import org.example.exception.InvalidBidException;
import org.example.model.Bidder;
import org.example.service.Auction;

public class AuctionServerController {
    private Auction auction;

    /**
     * ham nay duoc goi khi server nhan duoc goi tin yeu cau dat gia tu client
     * chi co logic tra ve du lieu
     */

    public BidResponse handleBidRequest(Bidder bidder, double bidAmount) {
        try {
            //goi tang logic vang ra loi
            auction.placeBid(bidder, bidAmount);
            //neu khong co loi nao vang ra
            return new BidResponse(true, "Dat gia thanh cong!", auction.getCurrentHighestBid());

        } catch (InvalidBidException | AuctionClosedException e) {
            //bat loi nghiep vu gui cho client
            //ghi log ra console
            System.out.println("[SEVER LOG] bat loi nghiep vu tu user"); //+ bidder.getNane() ngay khi clone code
            return new BidResponse(false, e.getMessage(), auction.getCurrentHighestBid());

        } catch (Exception e) {
            //bat loi he thong
            //ghi log chi tiet loi ra server de sua
            e.printStackTrace();

            return new BidResponse(false, "Loi he thong, vui long thu lai sau!", auction.getCurrentHighestBid());
        }
    }
}
