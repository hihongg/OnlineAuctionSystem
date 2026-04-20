#  Hệ thống Đấu giá Trực tuyến (Online Auction System)

Hệ thống Đấu giá Trực tuyến mô hình Client-Server sử dụng Java Socket, Multithreading và CSDL MySQL. Dự án áp dụng kiến trúc Multi-module để đảm bảo tính đóng gói và dễ dàng mở rộng.

##  Phân công công việc (Team Roles)

Dự án được chia thành 3 mảng chính, phân nhiệm vụ rõ ràng để các thành viên code song song không bị conflict:

### 1. Thành viên 1: [Tên thành viên 1] - Giao diện & Trải nghiệm (Client GUI)
* Thiết kế và lập trình giao diện người dùng (Java Swing / JavaFX).
* Bắt các sự kiện click nút (Login, Register, Place Bid).
* Xử lý luồng hiển thị dữ liệu (cập nhật giá mới nhất, đồng hồ đếm ngược) theo thời gian thực trên màn hình Client.

### 2. Thành viên 2: [Tên của bạn] - Logic cốt lõi & Xử lý Đa luồng (Core Logic & Multithreading)
* **Xây dựng Models (`shared/models`):** Thiết kế cấu trúc dữ liệu hướng đối tượng (OOP) cho `User`, `Bidder`, `Seller`, `Item`, `Auction`...
* **Xử lý Đấu giá (`AuctionService`):** Viết các thuật toán xác định người thắng cuộc, kiểm tra tính hợp lệ của bước giá.
* **Xử lý Đa luồng (Multithreading):** Giải quyết bài toán Race Condition bằng từ khóa `synchronized`, đảm bảo hệ thống không bị lỗi khi hàng trăm người cùng ấn nút "Đặt giá" vào cùng một phần nghìn giây.

### 3. Thành viên 3: [Tên thành viên 3] - Mạng & Cơ sở dữ liệu (Network Socket & Database)
* **Kết nối Mạng (`network`):** Khởi tạo Server Socket, quản lý danh sách các Client đang kết nối, gửi/nhận tín hiệu qua lại giữa Client và Server.
* **Quản lý CSDL (`dao` & `utils`):** Thiết lập cấu trúc Singleton kết nối MySQL (`DatabaseConnection`). Viết các lệnh truy vấn SQL (Data Access Object - DAO) để lưu trữ vĩnh viễn thông tin người dùng và lịch sử đấu giá.

---

## Cấu trúc dự án (Multi-module Architecture)

Dự án được quản lý bằng Maven, chia làm 3 module chính:
* `shared`: Chứa các lớp dùng chung (Models, Utils, Constants). Cả Client và Server đều phụ thuộc vào module này.
* `server`: Chứa logic xử lý nghiệp vụ, kết nối Database, xử lý đa luồng và đón các kết nối Socket từ Client.
* `client`: Chứa giao diện người dùng (GUI) và logic gửi yêu cầu (Request) lên Server.

##  Công nghệ sử dụng
* **Ngôn ngữ:** Java 17+
* **Mạng:** Java Socket (TCP/IP)
* **Đồng thời:** Java Multithreading & Synchronization
* **Cơ sở dữ liệu:** MySQL & JDBC
* **Quản lý dự án:** Maven & Git

## Hướng dẫn cài đặt & Chạy ứng dụng

Hệ thống yêu cầu cài đặt sẵn **Java JDK 17+**, **MySQL** và **Maven**.

### 1. Cấu hình Cơ sở dữ liệu (Database)
1. Bật MySQL Server (thông qua XAMPP, WAMP hoặc MySQL Workbench).
2. Tạo một Database mới có tên là `auction_db`.
3. Import file script `auction_db.sql` (đính kèm trong thư mục dự án) để tạo sẵn các bảng và dữ liệu mẫu.
4. Mở file `DatabaseConnection.java` (nằm trong `server/src/main/java/com/auction/server/utils/`).
5. Cập nhật lại `USER` và `PASSWORD` cho khớp với tài khoản MySQL trên máy tính của bạn.

### 2. Khởi động Hệ thống
Vì đây là mô hình Client-Server, bạn **bắt buộc** phải bật Server trước để nó mở cổng (Port) đón kết nối, sau đó mới bật giao diện Client.

**Bước 1: Khởi động Server (Bộ não hệ thống)**
* Mở project bằng IntelliJ IDEA.
* Chạy hàm `main` trong file `ServerApp.java` (module `server`).
* Nếu Terminal hiện thông báo *"Server đang chạy trên port XXXX"* và *"Kết nối cơ sở dữ liệu thành công"* là đã sẵn sàng.

**Bước 2: Mở Giao diện Người chơi (Client GUI)**
* Chạy hàm `main` trong file `ClientApp.java` (module `client`).
* Cửa sổ giao diện Ứng dụng Đấu giá sẽ hiện lên. Bạn có thể tiến hành Đăng ký / Đăng nhập.
* **💡 Mẹo test đa luồng:** Bạn có thể bấm Run file `ClientApp.java` nhiều lần để mở ra nhiều cửa sổ ứng dụng cùng lúc. Việc này giúp giả lập tình huống 2-3 người chơi khác nhau cùng vào xem và tranh nhau đặt giá một món đồ.