import os
from googleapiclient.discovery import build
from google.oauth2 import service_account

# Cấu hình đường dẫn key
KEY_FILE = 'workhub-amapi-key.json'
SCOPES = ['https://googleapis.com']

credentials = service_account.Credentials.from_service_account_file(KEY_FILE, scopes=SCOPES)
androidmanagement = build('androidmanagement', 'v1', credentials=credentials)

# 1. Tạo một Signup URL để liên kết doanh nghiệp của bạn với Google
signup_url = androidmanagement.signupUrls().create(
    callbackUrl="https://workhub-c0my.onrender.com" # Hoặc webhook URL của Supabase/Server của bạn
).execute()

print(f"BƯỚC 1: Hãy copy link này dán vào trình duyệt và đăng nhập bằng Gmail công ty để đăng ký:")
print(signup_url['url'])
print("\nBƯỚC 2: Sau khi đăng ký xong trên web, trình duyệt sẽ chuyển hướng về trang localhost.")
print("Hãy copy tham số 'enterpriseToken' trên thanh địa chỉ URL và dán vào đây.")

enterprise_token = input("Nhập enterpriseToken: ")

# 2. Hoàn tất đăng ký để lấy Enterprise ID
enterprise = androidmanagement.enterprises().create(
    enterpriseToken=enterprise_token
).execute()

print("\nĐĂNG KÝ THÀNH CÔNG!")
print(f"Mã Enterprise ID của WorkHub là: {enterprise['name']}")
