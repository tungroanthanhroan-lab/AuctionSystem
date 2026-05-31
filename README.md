# Hệ Thống Đấu Giá Trực Tuyến (Online Auction System)

## 1. Mô tả bài toán và hệ thống
Dự án là một hệ thống đấu giá trực tuyến được xây dựng theo mô hình **Client-Server**. Hệ thống cho phép người dùng đăng ký, đăng nhập với các vai trò khác nhau (Người dùng/Seller, Admin) để tham gia vào các phiên đấu giá.

Người bán có thể tạo phiên đấu giá cho sản phẩm của mình, trong khi người mua có thể xem danh sách sản phẩm, đặt giá (bid) thời gian thực và theo dõi lịch sử đấu giá. Server chịu trách nhiệm quản lý kết nối, đồng bộ dữ liệu đa luồng, cập nhật trạng thái đấu giá và thông báo tức thời cho các Client đang kết nối.

## 2. Công nghệ sử dụng và Yêu cầu cài đặt

### Công nghệ sử dụng:
- **Ngôn ngữ:** Java 21
- **Giao diện người dùng (GUI):** JavaFX 21
- **Cơ sở dữ liệu:** SQLite (nhẹ, lưu trữ cục bộ qua file `auction.db`)
- **Quản lý dự án & Build:** Maven 3.9.6 (đã đính kèm sẵn trong thư mục `maven/`)
- **Giao tiếp mạng:** TCP Socket (`java.net.Socket`, `ObjectOutputStream` / `ObjectInputStream`)
- **Khác:** Gson (xử lý JSON), JUnit 5 (Unit test)

### Yêu cầu cài đặt:
- **JDK 21** trở lên — tải tại [oracle.com](https://www.oracle.com/java/technologies/downloads/).
- **Không cần cài Maven riêng** — dự án đã đính kèm Maven 3.9.6 sẵn trong thư mục `maven/`.
- Hệ điều hành: Windows, macOS hoặc Linux.

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
├── service/      # Business logic, ClientHandler xử lý request từ Client, UserService...
├── util/         # Các tiện ích chung (như DatabaseConnection)
└── Main.java     # Lớp Main khởi chạy ứng dụng Client với giao diện JavaFX
```

## 4. Câu lệnh dòng lệnh để chạy chương trình

> **Lưu ý:** Dự án đã có sẵn Maven trong thư mục `maven/`, **không cần cài thêm Maven vào máy**.
> Tuy nhiên, bạn cần kích hoạt Maven cho phiên terminal trước khi dùng lệnh `mvn`.

---

### Bước 0: Mở terminal tại thư mục dự án
- **Windows:** Mở thư mục dự án bằng File Explorer → nhấp vào thanh địa chỉ → gõ `cmd` → Enter.
- **macOS / Linux:** Mở Terminal, gõ `cd ` (có khoảng trắng) rồi kéo thả thư mục dự án vào Terminal.

### Bước 1: Kích hoạt Maven (chỉ cần làm mỗi lần mở terminal mới)

**Windows (CMD):**
```cmd
set MAVEN_HOME=%cd%\maven\apache-maven-3.9.6
set PATH=%MAVEN_HOME%\bin;%PATH%
```

**macOS / Linux:**
```bash
export MAVEN_HOME=$(pwd)/maven/apache-maven-3.9.6
export PATH=$MAVEN_HOME/bin:$PATH
```

Kiểm tra kích hoạt thành công: `mvn -version` → sẽ hiển thị `Apache Maven 3.9.6`.

### Bước 2: Biên dịch dự án (chỉ cần làm 1 lần sau khi clone)
```bash
mvn clean install -DskipTests
```

### Bước 3: Chạy Server (bắt buộc khởi động trước)
```bash
mvn exec:java -Dexec.mainClass="org.example.server.AuctionServer"
```
Đợi terminal hiển thị: `[Server] Đang lắng nghe trên cổng 8080...` là Server đã sẵn sàng.

### Bước 4: Chạy Client (mở terminal mới, lặp lại Bước 0 và Bước 1 trước)

**Giao diện JavaFX (khuyên dùng):**
```bash
mvn javafx:run
```

**Giao diện dòng lệnh CLI (tuỳ chọn):**
```bash
mvn exec:java -Dexec.mainClass="org.example.client.AuctionClient"
```

## 5. Hướng dẫn chạy Server/Client theo thứ tự cụ thể
Để hệ thống hoạt động ổn định và tránh lỗi kết nối (`Connection Refused`), bạn **bắt buộc** phải tuân thủ thứ tự sau:

1. **Khởi động Server trước:** Chờ thông báo `[Server] Đang lắng nghe trên cổng 8080...`. Server sẽ tự động tạo cơ sở dữ liệu nếu chưa có.
2. **Khởi động Client:** Mở một (hoặc nhiều) cửa sổ terminal mới, kích hoạt Maven rồi chạy Client. Có thể mở bao nhiêu Client tùy ý, mỗi Client được Server cấp một Thread riêng (tối đa 100 client đồng thời).
3. **Thao tác:** Đăng ký tài khoản, đăng nhập, tạo phiên đấu giá và tham gia đặt giá.
4. **Tắt hệ thống:** Tắt tất cả Client trước, sau đó nhấn `Ctrl + C` trên terminal Server để dừng.

## 6. Danh sách chức năng đã hoàn thành
- **Quản lý Tài khoản:** Đăng ký, Đăng nhập, Phân quyền (USER/SELLER và ADMIN). Đổi mật khẩu.
- **Quản lý Ví tiền (Tài khoản ảo):** Nạp tiền (Deposit), Xem số dư ví (Check Balance).
- **Quản lý Đấu giá (Dành cho Seller/Admin):**
  - Tạo phiên đấu giá mới với giá khởi điểm.
  - Xem danh sách "Phiên đấu giá của tôi" (My Auctions).
  - Đóng phiên đấu giá (chỉ Admin hoặc chủ sở hữu phiên).
- **Tham gia Đấu giá (Dành cho mọi User):**
  - Xem danh sách các sản phẩm đang được đấu giá (View Items).
  - Đặt giá (Bid) — giá mới phải cao hơn giá hiện tại.
  - Xem lịch sử đặt giá của một phiên (Get Bid History).
- **Chức năng nâng cao:**
  - Đấu giá tự động (Auto-bidding) với mức giá trần và bước tăng giá.
  - Chống bắn tỉa (Anti-sniping) — tự động gia hạn phiên khi có bid vào phút cuối.
  - Biểu đồ lịch sử giá thời gian thực (Bid History Visualization).
- **Real-time (Thời gian thực):** Thông báo ngay lập tức cho tất cả Client khi có giá mới thông qua Observer Pattern và TCP Socket.

## 7. Link báo cáo và Video Demo
- **Báo cáo hệ thống (PDF):** [Bao_cao_he_thong.pdf](./Bao_cao_he_thong.pdf)
- **Video Demo:** [Link Video Demo](#) *(Cập nhật link video YouTube/Drive tại đây)*
