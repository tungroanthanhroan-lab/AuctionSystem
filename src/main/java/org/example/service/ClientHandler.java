package org.example.service;

import org.example.dao.BidDAO;
import org.example.model.Auction;
import org.example.model.User;
import org.example.observer.AuctionNotifier;
import org.example.observer.AuctionObserver;
import org.example.observer.BidUpdateEvent;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable, AuctionObserver {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final UserService userService;
    private final AuctionService auctionService;
    private final AuctionNotifier auctionNotifier;
    private final BidDAO bidDAO;

    // Lưu thông tin người dùng đăng nhập để ghi log bid đúng userId
    private User loggedInUser;

    public ClientHandler(Socket socket, UserService userService,
                         AuctionService auctionService, AuctionNotifier auctionNotifier,
                         BidDAO bidDAO) {
        this.socket = socket;
        this.userService = userService;
        this.auctionService = auctionService;
        this.auctionNotifier = auctionNotifier;
        this.bidDAO = bidDAO;
    }

    @Override
    public void run() {
        try {
            // LUÔN tạo Output trước Input để tránh Deadlock TCP
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Đăng ký nhận thông báo real-time
            auctionNotifier.addObserver(this);
            Object incomingData;

            while ((incomingData = in.readObject()) != null) {
                if (incomingData instanceof String) {
                    String command = (String) incomingData;
                    System.out.println("[Server] Nhận lệnh từ client " + socket.getInetAddress() + ": " + command);
                    handleCommand(command);
                }
            }
        } catch (EOFException e) {
            System.out.println("[Server] Client " + socket.getInetAddress() + " ngắt kết nối.");
        } catch (Exception e) {
            System.out.println("[Server] Lỗi kết nối với client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleCommand(String command) {
        try {
            if ("VIEW_ITEMS".equals(command)) {
                handleViewItems();
            } else if (command.startsWith("LOGIN")) {
                handleLogin(command);
            } else if (command.startsWith("REGISTER")) {
                handleRegister(command);
            } else if (command.startsWith("BID")) {
                handleBid(command);
            } else if (command.startsWith("CREATE_AUCTION")) {
                handleCreateAuction(command);
            } else {
                sendResponse("FAIL|Không hiểu lệnh: " + command);
            }
        } catch (IOException e) {
            System.err.println("[Server] Lỗi gửi response: " + e.getMessage());
        }
    }

    private void handleViewItems() throws IOException {
        List<Auction> auctions = auctionService.getActiveAuctionsList();
        StringBuilder sb = new StringBuilder("AUCTIONS");
        for (Auction a : auctions) {
            sb.append("|").append(a.getAuctionId())
                    .append(",").append(a.getCurrentHighestBid())
                    .append(",").append(a.getStatus());
        }
        sendResponse(sb.toString());
    }

    private void handleLogin(String command) throws IOException {
        String[] parts = command.split("\\|");
        if (parts.length == 3) {
            User user = userService.login(parts[1], parts[2]);
            if (user != null) {
                loggedInUser = user;
                sendResponse("SUCCESS|Chào mừng " + user.getUsername() + "! Role: " + user.getRole());
            } else {
                sendResponse("FAIL|Sai tài khoản hoặc mật khẩu");
            }
        } else {
            sendResponse("FAIL|Sai format. Dùng: LOGIN|username|password");
        }
    }

    private void handleRegister(String command) throws IOException {
        String[] parts = command.split("\\|");
        if (parts.length == 4) {
            boolean ok = userService.register(parts[1], parts[2], parts[3]);
            sendResponse(ok ? "SUCCESS|Đăng ký thành công" : "FAIL|Username đã tồn tại");
        } else {
            sendResponse("FAIL|Sai format. Dùng: REGISTER|username|password|role");
        }
    }

    private void handleBid(String command) throws IOException {
        String[] parts = command.split("\\|");
        if (parts.length != 3) {
            sendResponse("FAIL|Sai format. Dùng: BID|auctionId|amount");
            return;
        }

        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi đặt giá");
            return;
        }

        try {
            String auctionId = parts[1];
            double amount = Double.parseDouble(parts[2]);
            String bidderName = loggedInUser.getUsername();

            boolean success = auctionService.placeBid(auctionId, bidderName, amount);

            if (success) {
                bidDAO.placeBid(Integer.parseInt(auctionId), loggedInUser.getId(), amount);
                sendResponse("SUCCESS|Đặt giá " + amount + " thành công!");
            } else {
                sendResponse("FAIL|Đặt giá thất bại. Giá quá thấp hoặc phiên đã đóng.");
            }

        } catch (NumberFormatException e) {
            sendResponse("FAIL|Số tiền không hợp lệ");
        }
    }

    private void handleCreateAuction(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi tạo phiên đấu giá");
            return;
        }

        if (!"ADMIN".equalsIgnoreCase(loggedInUser.getRole())) {
            sendResponse("FAIL|Chỉ ADMIN mới được tạo phiên đấu giá");
            return;
        }

        String[] parts = command.split("\\|");
        if (parts.length != 4) {
            sendResponse("FAIL|Sai format. Dùng: CREATE_AUCTION|itemId|startingPrice|endTime");
            return;
        }

        try {
            int itemId = Integer.parseInt(parts[1]);
            double startingPrice = Double.parseDouble(parts[2]);
            String endTime = parts[3];

            boolean success = auctionService.createAuction(itemId, startingPrice, endTime);
            if (success) {
                sendResponse("SUCCESS|Phiên đấu giá đã được tạo thành công");
            } else {
                sendResponse("FAIL|Tạo phiên đấu giá thất bại. Kiểm tra itemId và endTime.");
            }
        } catch (NumberFormatException e) {
            sendResponse("FAIL|itemId hoặc startingPrice không hợp lệ");
        }
    }

    @Override
    public void onBidUpdate(BidUpdateEvent event) {
        try {
            out.writeObject(event);
            out.flush();
        } catch (IOException e) {
            System.out.println("[Notifier] Không gửi được event → tự động remove client.");
            auctionNotifier.removeObserver(this);
        }
    }

    private void sendResponse(String message) throws IOException {
        out.writeObject(message);
        out.flush();
    }

    private void cleanup() {
        try {
            auctionNotifier.removeObserver(this);
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}