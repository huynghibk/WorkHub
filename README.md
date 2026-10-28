# WorkHub

App Android quản lý công việc, ghi chú và chat nội bộ cho một công ty. Backend chạy trên máy tính hoặc cloud; app Android kết nối vào đó.

**Deploy public (Render / Fly miễn phí):** xem [DEPLOY.md](DEPLOY.md).

## Chạy backend

Trong PowerShell, tại thư mục project:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android Studio\jbr"
.\gradlew.bat :server:run
```

Máy chủ lắng nghe `http://localhost:8080`. Lần đầu Windows có thể hỏi quyền firewall cho Java. Dữ liệu lưu tại `server/data`.

## Chạy app

1. Mở Android Studio → **Open** → chọn thư mục `E:\Source\Android`.
2. Lần đầu Studio sẽ tải Gradle và các thư viện. JDK dùng bản đi kèm Android Studio (`jbr`).
3. Tạo một emulator, rồi bấm Run cho module `app`.
4. Địa chỉ máy chủ mặc định trên emulator là `http://10.0.2.2:8080`.

Trên điện thoại thật, điện thoại và máy tính phải cùng mạng Wi-Fi. Đổi địa chỉ máy chủ thành `http://<IP-máy-tính>:8080`.

## Dùng thử

1. Đăng ký và chọn **Tạo công ty**.
2. Ở màn Công việc, sao chép mã mời.
3. Tài khoản khác chọn **Tham gia** và nhập mã đó.
4. Tạo việc, ghi chú, rồi nhắn trong kênh **Chung**.

## Ghi chú

Bản này dùng HTTP trong mạng nội bộ và khóa JWT mặc định. Trước khi đưa ra ngoài công ty, hãy đặt biến môi trường `WORKHUB_JWT_SECRET` và chuyển sang HTTPS.
