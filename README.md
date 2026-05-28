# Hệ Thống Đấu Giá Trực Tuyến (Online Auction System)

## 1. Mô tả bài toán và hệ thống
Dự án là một hệ thống đấu giá trực tuyến được xây dựng theo mô hình **Client-Server**. Hệ thống cho phép người dùng đăng ký, đăng nhập với các vai trò khác nhau (Người dùng bình thường/Seller, Admin) để tham gia vào các phiên đấu giá. 
Người bán có thể tạo phiên đấu giá cho sản phẩm của mình, trong khi người mua có thể xem danh sách sản phẩm, đặt giá (bid) thời gian thực và theo dõi lịch sử đấu giá. Server chịu trách nhiệm quản lý kết nối, đồng bộ dữ liệu đa luồng, cập nhật trạng thái đấu giá và thông báo tức thời cho các Client đang kết nối.

## 2. Công nghệ sử dụng và Yêu cầu cài đặt
### Công nghệ sử dụng:
- **Ngôn ngữ:** Java 21
- **Giao diện người dùng (GUI):** JavaFX 21
- **Cơ sở dữ liệu:** SQLite (nhẹ, lưu trữ cục bộ qua file `auction.db`)
- **Quản lý dự án & Build:** Maven
- **Giao tiếp mạng:** TCP Socket (Java `java.net.Socket`, `ObjectOutputStream` / `ObjectInputStream`)
- **Khác:** Gson (xử lý JSON), JUnit 5 (Unit test)

### Yêu cầu cài đặt:
- **JDK 21** trở lên.
- **Maven 3.8+** đã được cài đặt và cấu hình biến môi trường (PATH).
- Một hệ điều hành tương thích (Windows, macOS, hoặc Linux).

## 3. Cấu trúc thư mục (Các module chính)
Dự án được cấu trúc theo mô hình MVC kết hợp Client-Server:
```text
src/main/java/org/example/
├── client/       # Chứa logic Client kết nối giao diện dòng lệnh (CLI)
├── controller/   # Các JavaFX Controller điều khiển giao diện UI (Login, Home, Bidding...)
├── dao/          # Data Access Object xử lý tương tác trực tiếp với SQLite (UserDAO, BidDAO...)
├── exception/    # Các class ngoại lệ (Exception) tùy chỉnh
├── model/        # Các thực thể dữ liệu (User, Auction, Item, BidTransaction...)
├── observer/     # Mẫu thiết kế Observer dùng để thông báo Real-time cho các client
├── server/       # Khởi chạy AuctionServer, quản lý ThreadPool
├── service/      # Chứa Business logic, ClientHandler xử lý request từ Client, UserService...
├── util/         # Các tiện ích chung (như DatabaseConnection)
└── Main.java     # Lớp Main khởi chạy ứng dụng Client với giao diện JavaFX
```

## 4. Câu lệnh dòng lệnh để chạy chương trình
Dự án sử dụng Maven, do đó các câu lệnh chạy tương tự nhau trên cả Windows, macOS và Linux.

**Bước 1: Clone và di chuyển vào thư mục dự án**
```bash
cd AuctionSystem
```

**Bước 2: Cài đặt và biên dịch dự án**
```bash
mvn clean install
```

**Bước 3: Chạy Server (Bắt buộc chạy trước)**
```bash
mvn exec:java -Dexec.mainClass="org.example.server.AuctionServer"
```

**Bước 4: Chạy Client (Giao diện JavaFX)**
Mở một terminal/command prompt mới và chạy:
```bash
mvn javafx:run
```
*(Hoặc sử dụng: `mvn exec:java -Dexec.mainClass="org.example.Main"`)*

**Chạy Client (Giao diện dòng lệnh CLI - Tùy chọn):**
```bash
mvn exec:java -Dexec.mainClass="org.example.client.AuctionClient"
```

## 5. Hướng dẫn chạy Server/Client theo thứ tự cụ thể
Để hệ thống hoạt động ổn định và tránh lỗi kết nối (`Connection Refused`), bạn **bắt buộc** phải tuân thủ thứ tự sau:

1. **Khởi động Server:** Chạy câu lệnh khởi động `AuctionServer` trước. Đợi cho đến khi terminal hiển thị dòng thông báo `[Server] Đang lắng nghe trên cổng 8080...`. Lúc này Server đã sẵn sàng nhận kết nối và tự động khởi tạo cơ sở dữ liệu (tạo bảng nếu chưa có).
2. **Khởi động Client:** Mở một (hoặc nhiều) cửa sổ terminal khác để chạy `Main` (JavaFX UI) hoặc `AuctionClient` (CLI). Bạn có thể mở bao nhiêu Client tùy ý, mỗi Client sẽ được Server tạo một Thread riêng để phục vụ (tối đa 100 client đồng thời).
3. **Thao tác:** Đăng ký tài khoản mới hoặc đăng nhập, tạo phiên đấu giá và tham gia đặt giá.
4. **Tắt hệ thống:** Khi không sử dụng, tắt tất cả các Client trước, sau đó tắt Server (nhấn `Ctrl + C` trên terminal chạy Server).

## 6. Danh sách chức năng đã hoàn thành
Hệ thống đã hoàn thiện các chức năng lõi sau:
- **Quản lý Tài khoản:** Đăng ký, Đăng nhập, Phân quyền người dùng (USER/SELLER và ADMIN). Đổi mật khẩu.
- **Quản lý Ví tiền (Tài khoản ảo):** Nạp tiền (Deposit), Xem số dư ví (Check Balance).
- **Quản lý Đấu giá (Dành cho Seller/Admin):**
  - Tạo phiên đấu giá mới với giá khởi điểm.
  - Xem danh sách "Phiên đấu giá của tôi" (My Auctions).
  - Đóng phiên đấu giá (Chỉ Admin hoặc chủ sở hữu phiên mới được đóng).
- **Tham gia Đấu giá (Dành cho mọi User):**
  - Xem danh sách các sản phẩm đang được đấu giá (View Items).
  - Đặt giá (Bid) kiểm tra logic giá phải cao hơn giá hiện tại.
  - Xem lịch sử đặt giá của một phiên cụ thể (Get Bid History).
- **Real-time (Thời gian thực):** Thông báo ngay lập tức cho các Client khi có một người chơi đặt giá mới thành công thông qua mẫu thiết kế Observer và TCP Socket.
