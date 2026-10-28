# Deploy WorkHub API (public, miễn phí)

Mục tiêu: backend có URL công khai (`https://….onrender.com`) để mọi máy/app đều gọi được.

## Lựa chọn miễn phí thực tế

| Dịch vụ | Miễn phí? | Ghi chú |
|--------|-----------|--------|
| **[Render](https://render.com)** | Có (Free Web Service) | Dễ nhất. Máy **ngủ** sau ~15 phút không dùng → lần mở lại chậm ~30–60s. Disk free **không bền** (redeploy có thể mất DB H2). |
| **[Fly.io](https://fly.io)** | Có hạn mức free | Cần cài `flyctl`, thẻ tín dụng để verify (thường không trừ tiền nếu trong hạn mức). |
| **[Koyeb](https://www.koyeb.com)** | Có Free | Tương tự Render. |
| **Google Cloud Run** | Free tier | Cần tài khoản Google Cloud; trả theo request, thường $0 với traffic nhỏ. |
| Oracle Always Free | Có | Bạn đang kẹt đăng ký → bỏ qua. |

**Không có** VPS JVM “luôn online + disk bền + 0 đồng mãi mãi” ổn định. Free tier đủ để **demo / test công khai**; production thật nên trả phí nhẹ hoặc dùng Postgres miễn phí (Neon) sau.

Khuyến nghị bắt đầu: **Render Free**.

---

## Deploy lên Render (khuyến nghị)

1. Đẩy code lên **GitHub** (repo public hoặc private).
2. Vào [https://dashboard.render.com](https://dashboard.render.com) → Sign up (GitHub).
3. **New → Web Service** → chọn repo WorkHub.
4. Cấu hình:
   - **Runtime:** Docker
   - **Dockerfile path:** `./Dockerfile`
   - **Plan:** Free
   - Health check: `/health`
5. (Tuỳ chọn) Import từ `render.yaml` trong repo: **New → Blueprint**.
6. Deploy xong, lấy URL dạng:
   `https://workhub-api-xxxx.onrender.com`
7. Kiểm tra trình duyệt: `https://workhub-api-xxxx.onrender.com/health` → phải ra `ok`.

### App Android

Trên màn đăng nhập, ô **Địa chỉ máy chủ** điền:

```text
https://workhub-api-xxxx.onrender.com
```

(không thêm `/` cuối cũng được). Mọi user dùng cùng URL đó.

Lần đầu sau khi server ngủ: mở `/health` trên trình duyệt chờ vài chục giây rồi mới đăng nhập app.

---

## Deploy lên Fly.io (tuỳ chọn)

```powershell
# Cài flyctl rồi:
fly auth login
fly launch --dockerfile Dockerfile --name workhub-api --region sin
fly secrets set WORKHUB_JWT_SECRET=mot-chuoi-bi-mat-dai
fly deploy
```

URL: `https://workhub-api.fly.dev`

---

## Lưu ý quan trọng

- DB hiện là **H2 file** trong container → free tier **mất dữ liệu** khi redeploy / sleep (Render). Demo OK; lâu dài nên chuyển Postgres (Neon free) — có thể làm bước sau.
- Đặt `WORKHUB_JWT_SECRET` khác giá trị mặc định trên môi trường production.
- WebSocket chat cần host hỗ trợ WS (Render/Fly đều được).
