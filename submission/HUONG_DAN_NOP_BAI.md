# Hồ sơ nộp bài – Mai Van Viet – BIT240253

Kho GitHub: https://github.com/vanviet2k6-creator/BaiKiemKtraGiuaKi

1. Nhấn **Code → Download ZIP** hoặc dùng `git clone` để tải mã nguồn. Mở thư mục gốc bằng Android Studio. Tên project là `BIT240253_FoodManager`.
2. Chạy module `app` hoặc cài `submission/BIT240253_FoodManager.apk` trên Android API 26 trở lên.
3. Xem/tải `submission/Video_1_BIT240253_Demo_ung_dung.mp4`. Video có danh tính, danh sách, chi tiết, thêm/sửa/xóa, lọc/sắp xếp, ảnh, đổi Anh–Việt và tắt/bật mạng để tự đồng bộ MockAPI.
4. Đọc `submission/Bao_cao_BIT240253_FoodManager.pdf` và README để giải thích mã nguồn khi bảo vệ.
5. Nộp link kho Git và Video 1 lên LMS trong ca kiểm tra theo đề. Video 2 nộp riêng trên LMS chậm nhất **23/10/2026**.

**Không tải Video 2 hoặc tệp phụ của Video 2 lên kho này theo yêu cầu sinh viên.**

## API đã cấu hình sẵn

- Project: https://mockapi.io/projects/6ad7138df71f154ac487b474
- Base URL: https://6ad7138df71f154ac487b473.mockapi.io/api/v1
- Resource: `BIT240253_DishCategory`, `BIT240253_Dish`.
- Mọi trường dữ liệu gồm cả khóa remote đều có tiền tố `BIT240253_`.

## Kiểm thử đã chạy

- `assembleDebug` và `assembleDebugAndroidTest`: thành công.
- `lintDebug`: 0 lỗi, 5 cảnh báo.
- Instrumentation chạy SQLite Android thật: **62/62 kiểm tra đạt**.
- Tạo và sửa offline → tự gửi khi bật mạng → đối chiếu đúng 1 bản ghi trên MockAPI thật; xóa được đồng bộ lên remote.

Mã nguồn ZIP chỉ có project và tài liệu, không gồm video, APK hay cache build. Tệp Video 1, APK và báo cáo đặt riêng trong `submission/` để tải thuận tiện.
