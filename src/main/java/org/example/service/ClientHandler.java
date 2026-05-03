package org.example.service;

import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable, AuctionObserve{
    private Socket socket;
    private ObjectOutputStream out;

    public ClientHandler(Socket socket, ObjectOutputStream out) {
        this.socket = socket;
        this.out = out;
    }

    @Override
    public void onBidUpdate(BidUpdateEvent event) {
        try {
            //dua event payload truc tiep qua mang cho frontend
            out.writeObject(event);
            out.flush();
        } catch (Exception e) {
            System.out.println("Khong the gui ket noi cho client nay");
        }
    }
    @Override
    public void run() {

    }


}
