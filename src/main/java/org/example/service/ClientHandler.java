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
 * Protocol lệnh được hỗ trợ:
 *   LOGIN|username|password
 *   REGISTER|username|password|role
 *   VIEW_ITEMS
 *   CREATE_AUCTION|title|startingPrice|endTime
 *   BID|auctionId|amount
 *   CLOSE_AUCTION|auctionId
 *   GET_BID_HISTORY|auctionId
 */
public class ClientHandler implements Runnable, AuctionObserver {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final UserService userService;
    private final AuctionService auctionService;
    private final AuctionNotifier auctionNotifier;
    private final BidDAO bidDAO;

    // Lưu user đang đăng nhập — dùng để ghi bid đúng userId vào DB
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

            } else if (command.startsWith("CREATE_AUCTION")) {
                handleCreateAuction(command);

            } else if (command.startsWith("BID")) {
                handleBid(command);

            } else if (command.startsWith("CLOSE_AUCTION")) {
                handleCloseAuction(command);

            } else if (command.startsWith("GET_BID_HISTORY")) {
                handleGetBidHistory(command);

            } else if (command.startsWith("CHANGE_PASSWORD")) {
                handleChangePassword(command);

            } else if (command.startsWith("DEPOSIT")) {
                handleDeposit(command);

            } else if (command.startsWith("CHECK_BALANCE")) {
                handleCheckBalance(command);

            } else if (command.startsWith("MY_AUCTIONS")) {
                handleMyAuctions();

            } else {
                sendResponse("FAIL|Không hiểu lệnh: " + command);
            }
        } catch (IOException e) {
            System.err.println("[Server] Lỗi gửi response: " + e.getMessage());
        }
    }

    /**
     * Trả về danh sách phiên đấu giá đang mở.
     *
     * Format response:
     * AUCTIONS|auctionId,title,currentHighestBid,status,currentLeader,endTime|...
     *
     * Nếu không có leader → currentLeader = "-"
     * Nếu không có endTime → endTime = "-"
     */
    private void handleViewItems() throws IOException {
        List<Auction> auctions = auctionService.getActiveAuctionsList();
        StringBuilder sb = new StringBuilder("AUCTIONS");
        for (Auction a : auctions) {
            String title = "Không rõ sản phẩm";
            if (a.getItem() != null && a.getItem().getTitle() != null) {
                title = a.getItem().getTitle();
            }

            String leader = (a.getCurrentLeader() != null)
                    ? a.getCurrentLeader().getUsername()
                    : "-";

            String endTime = (a.getEndTime() != null)
                    ? a.getEndTime().toString()
                    : "-";

            sb.append("|")
              .append(a.getAuctionId()).append(",")
              .append(title).append(",")
              .append(a.getCurrentHighestBid()).append(",")
              .append(a.getStatus()).append(",")
              .append(leader).append(",")
              .append(endTime);
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

    /**
     * Seller/Admin tạo phiên đấu giá mới bằng tên sản phẩm.
     *
     * Format: CREATE_AUCTION|title|startingPrice|endTime
     * Ví dụ:  CREATE_AUCTION|Laptop Gaming|1000|2026-12-31T23:59
     */
    private void handleCreateAuction(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi tạo phiên đấu giá");
            return;
        }

        String role = loggedInUser.getRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"USER".equalsIgnoreCase(role)) {
            sendResponse("FAIL|Chỉ Seller hoặc Admin mới được tạo phiên đấu giá");
            return;
        }

        String[] parts = command.split("\\|", -1);
        if (parts.length != 4) {
            sendResponse("FAIL|Sai format. Dùng: CREATE_AUCTION|title|startingPrice|endTime");
            return;
        }

        try {
            String title = parts[1].trim();
            double startingPrice = Double.parseDouble(parts[2].trim());
            String endTime = parts[3].trim();

            if (title.isEmpty()) {
                sendResponse("FAIL|Tên sản phẩm không được để trống");
                return;
            }
            if (startingPrice < 0) {
                sendResponse("FAIL|Giá khởi điểm không được âm");
                return;
            }

            boolean success = auctionService.createAuctionWithNewItem(
                    title, "", startingPrice, endTime, loggedInUser.getId());

            sendResponse(success
                    ? "SUCCESS|Phiên đấu giá đã được tạo thành công"
                    : "FAIL|Tạo phiên đấu giá thất bại. Kiểm tra dữ liệu nhập.");

        } catch (NumberFormatException e) {
            sendResponse("FAIL|Giá khởi điểm không hợp lệ");
        }
    }

    /**
     * Format: BID|auctionId|amount
     */
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

    private void handleChangePassword(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước");
            return;
        }
        String[] parts = command.split("\\|");
        if (parts.length != 3) {
            sendResponse("FAIL|Sai format. Dùng: CHANGE_PASSWORD|oldPass|newPass");
            return;
        }
        boolean success = userService.changePassword(loggedInUser.getUsername(), parts[1], parts[2]);
        if (success) {
            sendResponse("SUCCESS|Đổi mật khẩu thành công");
        } else {
            sendResponse("FAIL|Sai mật khẩu cũ");
        }
    }

    private void handleDeposit(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước");
            return;
        }
        String[] parts = command.split("\\|");
        if (parts.length != 2) {
            sendResponse("FAIL|Sai format. Dùng: DEPOSIT|amount");
            return;
        }
        try {
            double amount = Double.parseDouble(parts[1]);
            if (amount <= 0) {
                sendResponse("FAIL|Số tiền nạp phải lớn hơn 0");
                return;
            }
            boolean success = userService.updateBalance(loggedInUser.getUsername(), amount);
            if (success) {
                sendResponse("SUCCESS|Nạp tiền thành công");
            } else {
                sendResponse("FAIL|Có lỗi xảy ra khi nạp tiền");
            }
        } catch (NumberFormatException e) {
            sendResponse("FAIL|Số tiền không hợp lệ");
        }
    }

    private void handleCheckBalance(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước");
            return;
        }
        double balance = userService.getBalance(loggedInUser.getUsername());
        if (balance >= 0) {
            sendResponse("BALANCE|" + balance);
        } else {
            sendResponse("FAIL|Lỗi khi lấy số dư");
        }
    }

    /** Nhận event real-time từ AuctionNotifier, đẩy ngay về client */
    @Override
    public void onBidUpdate(BidUpdateEvent event) {
        /*
         * Tạm thời không gửi BidUpdateEvent object về JavaFX client.
         * UI hiện đang chờ response dạng String (SUCCESS|... / FAIL|...).
         * Nếu gửi object event xen vào, client dễ đọc sai hoặc báo lỗi kết nối.
         */
        System.out.println("[Notifier] Có bid update, tạm thời không gửi realtime object về client.");
    }

    private void sendResponse(String message) throws IOException {
        out.writeObject(message);
        out.flush();
    }
    /**
     * Admin đóng phiên đấu giá.
     *
     * Protocol:
     * CLOSE_AUCTION|auctionId
     */
    private void handleCloseAuction(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi đóng phiên");
            return;
        }

        String[] parts = command.split("\\|");

        if (parts.length != 2) {
            sendResponse("FAIL|Sai format. Dùng: CLOSE_AUCTION|auctionId");
            return;
        }

        String auctionId = parts[1].trim();

        /*
         * Rule:
         * - ADMIN được đóng mọi phiên.
         * - USER chỉ được đóng phiên do chính mình tạo.
         */
        boolean isAdmin = "ADMIN".equalsIgnoreCase(loggedInUser.getRole());
        boolean isOwner = auctionService.isAuctionOwner(auctionId, loggedInUser.getId());

        if (!isAdmin && !isOwner) {
            sendResponse("FAIL|Bạn chỉ có thể đóng phiên do chính mình tạo");
            return;
        }

        boolean success = auctionService.closeAuction(auctionId);

        if (success) {
            sendResponse("SUCCESS|Phiên đấu giá đã được đóng thành công");
        } else {
            sendResponse("FAIL|Đóng phiên thất bại hoặc phiên không tồn tại");
        }
    }
    /**
     * Lấy lịch sử bid thật từ DB.
     *
     * Protocol:
     * GET_BID_HISTORY|auctionId
     *
     * Response:
     * BID_HISTORY|userA đã đặt 100.0 $ lúc ...|userB đã đặt 120.0 $ lúc ...
     */
    private void handleGetBidHistory(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi xem lịch sử đấu giá");
            return;
        }

        String[] parts = command.split("\\|");

        if (parts.length != 2) {
            sendResponse("FAIL|Sai format. Dùng: GET_BID_HISTORY|auctionId");
            return;
        }

        try {
            int auctionId = Integer.parseInt(parts[1].trim());

            List<String[]> history = bidDAO.getBidHistory(auctionId);

            StringBuilder sb = new StringBuilder("BID_HISTORY");

            for (String[] row : history) {  // Thay từ String line
                // row: [userId, username, bidAmount, bidTime]
                sb.append("|")
                        .append(row[1])   // username
                        .append(" đã đặt ")
                        .append(row[2])   // bidAmount
                        .append("$ lúc ")
                        .append(row[3]);  // bidTime
            }


            sendResponse(sb.toString());

        } catch (NumberFormatException e) {
            sendResponse("FAIL|auctionId không hợp lệ");
        }
    }
    /**
     * Lấy danh sách phiên đấu giá do user hiện tại tạo.
     *
     * Protocol:
     * MY_AUCTIONS
     *
     * Response:
     * MY_AUCTIONS|auctionId,title,currentHighestBid,status,endTime
     */
    private void handleMyAuctions() throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi xem phiên của tôi");
            return;
        }

        List<Auction> auctions = auctionService.getAuctionsBySellerId(loggedInUser.getId());

        StringBuilder sb = new StringBuilder("MY_AUCTIONS");

        for (Auction a : auctions) {
            String title = "Không rõ sản phẩm";
            String endTime = "";

            if (a.getItem() != null) {
                if (a.getItem().getTitle() != null) {
                    title = a.getItem().getTitle();
                }

                if (a.getItem().getEndTime() != null) {
                    endTime = a.getItem().getEndTime();
                }
            }

            sb.append("|").append(a.getAuctionId())
                    .append(",").append(title)
                    .append(",").append(a.getCurrentHighestBid())
                    .append(",").append(a.getStatus())
                    .append(",").append(endTime);
        }

        sendResponse(sb.toString());
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
