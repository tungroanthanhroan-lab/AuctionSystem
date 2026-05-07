package org.example.service;

import org.example.dao.UserDAO;
import org.example.model.Auction;
import org.example.model.User;
import org.example.model.Bidder;
import org.example.observer.AuctionNotifier;
import org.example.observer.AuctionObserver;
import org.example.observer.BidUpdateEvent;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable, AuctionObserver {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private UserService userService;
    private AuctionService auctionService;
    private AuctionNotifier auctionNotifier;

    public ClientHandler(Socket socket, UserService userService, AuctionService auctionService, AuctionNotifier auctionNotifier) {
        this.socket = socket;
        this.userService = userService;
        this.auctionService = auctionService;
        this.auctionNotifier = auctionNotifier;
    }

    @Override
    public void run() {
        try {
            // LUÔN tạo Output trước Input để tránh Deadlock TCP
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Đăng ký nhận thông báo realtime
            auctionNotifier.addObserve(this);

            Object incomingData;
            // Dùng in.readObject() thay vì readLine()
            while ((incomingData = in.readObject()) != null) {
                if (incomingData instanceof String) {
                    String command = (String) incomingData;
                    System.out.println("Client gửi: " + command);
                    handleCommand(command);
                }
            }
        } catch (EOFException e) {
            System.out.println("Client ngắt kết nối chủ động.");
        } catch (Exception e) {
            System.out.println("Lỗi kết nối Client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleCommand(String command) throws IOException {
        try {
            if ("VIEW_ITEMS".equals(command)) {
                sendResponse("Danh sách sản phẩm...");
            } else if (command.startsWith("LOGIN")) {
                String[] parts = command.split("\\|");

                if (parts.length == 3) {
                    User user = userService.login(parts[1], parts[2]);

                    if (user != null) {
                        sendResponse("SUCCESS|Welcome " + user.getUsername());
                    } else {
                        sendResponse("FAIL|Sai tài khoản hoặc mật khẩu");
                    }
                } else {
                    sendResponse("FAIL|Sai format LOGIN");
                }
            } else if (command.startsWith("REGISTER")) {
                String[] parts = command.split("\\|");

                if (parts.length == 4) {
                    boolean ok = userService.register(parts[1], parts[2], parts[3]);

                    if (ok) {
                        sendResponse("SUCCESS|Đăng ký thành công");
                    } else {
                        sendResponse("FAIL|Username đã tồn tại");
                    }
                } else {
                    sendResponse("FAIL|Sai format REGISTER");
                }
            } else if (command.startsWith("BID")) {
                // format: BID|auctionId|amount|bidderName
                String[] parts = command.split("\\|");

                if (parts.length == 4) {
                    try {
                        String auctionId = parts[1];
                        double amount = Double.parseDouble(parts[2]);
                        String bidderName = parts[3];

                        boolean sucess = auctionService.placeBid(auctionId, bidderName, amount);

                        Auction auction = new Auction(auctionId);
                        Bidder bidder = new Bidder(bidderName);

                        BidUpdateEvent event = new BidUpdateEvent(
                                auction,
                                amount,
                                bidder,
                                String.valueOf(System.currentTimeMillis())
                        );

                        auctionNotifier.broadcast(event);

                    } catch (NumberFormatException e) {
                        sendResponse("FAIL|Số tiền không hợp lệ");
                    }
                } else {
                    sendResponse("FAIL|Sai format BID");
                }
            } else {
                sendResponse("FAIL|Không hiểu lệnh");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // nhận event realtime từ AuctionNotifier
    @Override
    public void onBidUpdate(BidUpdateEvent event) {
        try {
            out.writeObject(event);
            out.flush();
        } catch (IOException e) {
            System.out.println("Không gửi được event → remove client");
            auctionNotifier.removeObserve(this);
        }
    }
    private void sendResponse(String message) throws IOException{
        out.writeObject(message);
        out.flush();
    }

    private void cleanup() {
        try {
            auctionNotifier.removeObserve(this);
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}