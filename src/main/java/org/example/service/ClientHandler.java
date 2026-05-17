package org.example.service;

import org.example.dao.BidDAO;
import org.example.model.Auction;
import org.example.model.User;
import org.example.model.Bidder;
import org.example.observer.AuctionNotifier;
import org.example.observer.AuctionObserver;
import org.example.observer.BidUpdateEvent;

import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * Xử lý một client kết nối — chạy trên Thread riêng.
 * Implements AuctionObserver để nhận thông báo real-time từ AuctionNotifier.
 *
 * FIX BUG 8: Đồng nhất protocol — dùng ObjectOutputStream/ObjectInputStream
 *            nhất quán với cách server ghi dữ liệu.
 * FIX BUG 5: Bỏ đoạn broadcast thừa trong case BID — AuctionService đã broadcast bên trong.
 */
public class ClientHandler implements Runnable, AuctionObserver {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final UserService userService;
    private final AuctionService auctionService;
    private final AuctionNotifier auctionNotifier;
    private final BidDAO bidDAO;

    // Lưu thông tin người dùng sau khi đăng nhập thành công để xử lý các lệnh BID
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
            // Vòng lặp nhận dữ liệu liên tục từ Client
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
            cleanup(); // Đảm bảo đóng socket và gỡ observer khi kết thúc
        }
    }
    /**
     * Phân tích lệnh (Protocol) gửi từ Client và điều hướng xử lý
     */
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

            } else {
                sendResponse("FAIL|Không hiểu lệnh: " + command);
            }
        } catch (IOException e) {
            System.err.println("[Server] Lỗi gửi response: " + e.getMessage());
        }
    }
    /** Gửi danh sách các phiên đấu giá đang diễn ra cho Client */
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
    /** Xử lý đăng nhập và lưu trạng thái session vào loggedInUser */
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
    /**
     * Xử lý lệnh đặt giá từ Client.
     * Kiểm tra đăng nhập và ghi log vào Database nếu đặt giá thành công.
     */
    private void handleBid(String command) throws IOException {
        // Format: BID|auctionId|amount
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

            // FIX BUG 5: Chỉ gọi placeBid() — broadcast đã được thực hiện BÊN TRONG AuctionService.
            //            Không tạo thêm event hay gọi broadcast() ở đây nữa.
            boolean success = auctionService.placeBid(auctionId, bidderName, amount);

            if (success) {
                // Ghi lịch sử bid vào DB (dùng userId thật)
                bidDAO.placeBid(Integer.parseInt(auctionId), loggedInUser.getId(), amount);
                sendResponse("SUCCESS|Đặt giá " + amount + " thành công!");
            } else {
                sendResponse("FAIL|Đặt giá thất bại. Giá quá thấp hoặc phiên đã đóng.");
            }

        } catch (NumberFormatException e) {
            sendResponse("FAIL|Số tiền không hợp lệ");
        }
    }

    /** Nhận event real-time từ AuctionNotifier, đẩy ngay về client */
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
    /** Gửi thông báo dạng chuỗi văn bản về Client */
    private void sendResponse(String message) throws IOException {
        out.writeObject(message);
        out.flush();
    }
    /** Dọn dẹp tài nguyên khi Client ngắt kết nối */
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