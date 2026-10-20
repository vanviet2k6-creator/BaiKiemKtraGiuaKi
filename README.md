# BIT240253_FoodManager

**Sinh viên: Mai Van Viet – BIT240253**  
Đề giữa kỳ: Phát triển ứng dụng di động – Quản lý món ăn.

Ứng dụng Android Native Java, lưu SQLite trên thiết bị và tự đồng bộ hai chiều với MockAPI. Hoạt động tạo/sửa/xóa không phụ thuộc kết nối mạng. Giao diện mặc định English; chuyển English ↔ Tiếng Việt tại ứng dụng.

## 1. Chạy ứng dụng

- Mở thư mục project này trong Android Studio; chọn JDK 21 (JDK đi kèm Android Studio mới cũng có thể phù hợp).
- Cài Android SDK Platform 35 và Build Tools. Cấu hình đường dẫn SDK qua Android Studio hoặc `local.properties` trên máy của bạn; file này không đưa lên Git.
- Gradle Wrapper 9.5.0, Android Gradle Plugin 9.3.2. Lần build đầu cần mạng để tải công cụ nếu máy chưa có cache.
- Chạy module `app` trên máy Android 8.0/API 26 trở lên hoặc emulator. Bản đã kiểm thử dùng emulator x86_64 API 37.2.
- Không cần đăng nhập trong app. Endpoint MockAPI đã cấu hình sẵn. Có thể sửa tại Settings → MockAPI base URL.

Windows:

```powershell
.\build.ps1 -Verify
```

Script tự tìm JDK/SDK hợp lệ trong phiên làm việc (hữu ích nếu `JAVA_HOME` cũ trỏ tới thư mục đã bị gỡ). Hoặc chạy Gradle trực tiếp sau khi cấu hình JDK:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest lintDebug
```

macOS/Linux:

```sh
chmod +x gradlew
./gradlew assembleDebug
```

APK được tạo tại `app/build/outputs/apk/debug/app-debug.apk`. Đây là bản debug có chữ ký phát triển, phục vụ cài đặt và demo bài thực hành.

## 2. MockAPI thật đã tạo cho bài

- Project: [BIT240253_FoodManager](https://mockapi.io/projects/6ad7138df71f154ac487b474).
- Base URL: `https://6ad7138df71f154ac487b473.mockapi.io/api/v1`.
- [Loại món](https://6ad7138df71f154ac487b473.mockapi.io/api/v1/BIT240253_DishCategory).
- [Món ăn](https://6ad7138df71f154ac487b473.mockapi.io/api/v1/BIT240253_Dish).

Hai resource nằm trong tài khoản MockAPI đang đăng nhập của người dùng. Mọi trường do bài quản lý đều có tiền tố `BIT240253_`, **bao gồm khóa ID do MockAPI sinh: `BIT240253_RemoteId`**. Không sử dụng trường mặc định `id`, `createdAt`, `name`, `avatar` trong schema đã cấu hình.

MockAPI là dịch vụ mô phỏng phục vụ thực hành, endpoint CRUD không có xác thực ứng dụng. Chỉ dùng dữ liệu món ăn thử nghiệm. Project hiện tại không thay đổi project quản lý khóa học đã có trước đó của người dùng.

## 3. Cấu trúc mã nguồn

```text
settings.gradle                         tên BIT240253_FoodManager
app/build.gradle                        cấu hình Android, applicationId
app/src/main/AndroidManifest.xml        quyền mạng, Activity khởi động
app/src/main/java/vn/edu/bit240253/foodmanager/
  Schema.java                           MSSV, tiền tố, tên bảng và hàm tên cột
  DishCategory.java                     model loại món
  Dish.java                             model món, isQuick(), thumbnail()
  DishImage.java                        model ảnh
  FoodDb.java                           SQLite, truy vấn, validation, transaction, Outbox
  SyncManager.java                      callback mạng, REST, push/pull, chống trùng
  Images.java                           tải ảnh nền và cache local
  MainActivity.java                     danh sách, form, chi tiết, loại món, cài đặt, đa ngôn ngữ
app/src/androidTest/.../FoodInstrumentation.java
                                        kiểm thử trực tiếp SQLite trên Android
```

Đây là giao diện Android thật, không dùng WebView hay trang web để thay thế ứng dụng di động.

## 4. Thiết kế dữ liệu và tiền tố MSSV

`Schema.PREFIX = "BIT240253_"`; `Schema.f("Name")` trả về `BIT240253_Name`. Dùng hằng số này cho toàn bộ SQL và JSON, tránh thiếu tiền tố. Tên thuộc tính Java ngắn gọn theo quy ước Java; tên **lưu trữ** tuân thủ yêu cầu đề.

| Bảng | Các cột đã lưu |
|---|---|
| BIT240253_DishCategory | BIT240253_Id (PK), BIT240253_Name, BIT240253_Description |
| BIT240253_Dish | BIT240253_Id (PK), BIT240253_Name, BIT240253_NormalizedName, BIT240253_Price, BIT240253_PreparationTime, BIT240253_IsAvailable, BIT240253_Description, BIT240253_DishCategoryId (FK) |
| BIT240253_DishImage | BIT240253_Id (PK), BIT240253_DishId (FK), BIT240253_ImageUrl, BIT240253_IsThumbnail, BIT240253_Position |
| BIT240253_Outbox | BIT240253_Entity + BIT240253_Id (PK kép), BIT240253_Action, BIT240253_Revision |
| BIT240253_RemoteMap | BIT240253_Entity + BIT240253_Id (PK kép), BIT240253_RemoteId |

Quan hệ: `DishCategory 1 → N Dish 1 → N DishImage`.

- SQLite bật foreign keys trong `onConfigure`.
- Xóa loại bị chặn bằng `ON DELETE RESTRICT` và kiểm tra nghiệp vụ có thông báo dễ hiểu.
- Xóa món xóa ảnh bằng `ON DELETE CASCADE`.
- Tên bắt buộc; giá và thời gian > 0 có kiểm tra Java và CHECK của SQLite.
- Tên chuẩn hóa bằng NFC + trim + lowercase. UNIQUE `(DishCategoryId, NormalizedName)` ngăn trùng tên trong cùng loại.
- Partial UNIQUE index của `DishImage` với `IsThumbnail=1` bảo đảm tối đa một ảnh đại diện.
- `Quick/Nhanh` nếu thời gian ≤ 15; `Regular/Thông thường` nếu > 15, không thêm cột phân loại.

Bảng `android_metadata`/các bảng nội bộ nếu Android hoặc SQLite tự sinh không thuộc schema nghiệp vụ. Bộ kiểm thử kiểm tra tất cả bảng và cột ứng dụng tự tạo đều có tiền tố.

## 5. Dữ liệu mẫu tối thiểu

Lần chạy đầu khởi tạo đúng 3 loại và 5 món, mỗi món có 2 ảnh URL. Cờ Seeded lưu lại để không tự thêm lại dữ liệu khi người dùng chủ động xóa toàn bộ món/loại.

| Loại | Món | Giá (VND) | Phút | Đang bán | Phân loại |
|---|---|---:|---:|---|---|
| Main dishes | Chicken rice | 55.000 | 20 | Có | Regular |
| Main dishes | Beef noodles | 65.000 | 25 | Có | Regular |
| Light bites | Garden salad | 35.000 | 10 | Có | Quick |
| Light bites | Spring rolls | 30.000 | 15 | Không | Quick |
| Drinks | Iced coffee | 25.000 | 5 | Có | Quick |

Ảnh Unsplash là ảnh minh họa món ăn, không phải ảnh chụp do sinh viên thực hiện. Không cần tải ảnh thành công để các chức năng dữ liệu hoạt động.

## 6. Chức năng và cách kiểm tra

1. **Danh sách:** hiển thị ảnh đại diện, tên, loại, giá, thời gian, trạng thái và Quick/Regular.
2. **Chi tiết:** chạm thẻ món; xem thông tin, nhiều ảnh, đổi ảnh đại diện, sửa hoặc xóa với xác nhận.
3. **Thêm/sửa:** tên không rỗng; giá là số hữu hạn > 0; thời gian là số nguyên > 0; chọn loại đã lưu. Lưu xong đọc lại từ SQLite.
4. **Loại món:** thêm/sửa/xóa; thử xóa một loại còn món để thấy thông báo bị chặn.
5. **Tìm kiếm:** nhập một phần tên; không phân biệt hoa/thường. Không bỏ dấu tiếng Việt. Ký tự `%`/`_` được hiểu là ký tự tìm kiếm thông thường.
6. **Lọc kết hợp:** mở Filters & sorting; chọn loại, trạng thái, giá từ/đến đồng thời. Khoảng giá sai hiển thị lỗi ngay.
7. **Sắp xếp:** giá tăng, giá giảm, thời gian chế biến tăng hoặc tên. Mở chi tiết rồi quay lại để kiểm tra bộ lọc vẫn giữ.
8. **Ảnh:** nhập HTTPS ImageURL → Add image. Chọn Set cover, bỏ ảnh, xem thư viện ở chi tiết. Có thể lưu món không ảnh. URL sai cú pháp bị chặn; ảnh không tải được hiển thị placeholder.
9. **Ngôn ngữ:** VI/EN trên danh sách và chi tiết; nút ngôn ngữ trong form. Nhãn, nút, trạng thái, validation và lỗi được dịch. Lựa chọn được nhớ giữa các màn hình và lần mở app sau.
10. **Offline:** tắt cả Wi-Fi và mobile data của thiết bị/emulator; tạo/sửa/xóa vẫn lưu SQLite. Bật lại mạng; theo dõi thanh trạng thái và endpoint MockAPI, không bấm nút đồng bộ.

Tên và mô tả người dùng nhập được giữ nguyên, không tự dịch.

## 7. Luồng đồng bộ nâng cao

```text
Form / thao tác xóa
   → validation
   → SQLite transaction: dữ liệu + Outbox
   → giao diện đọc lại SQLite
   → SyncManager.request()
      ├─ offline / lỗi HTTP → giữ nguyên Outbox, báo trạng thái, thử lại sau
      └─ online → đọc remote → POST/PUT/DELETE → ACK theo revision
                  → đọc remote lần nữa → nhập loại rồi món → đối chiếu xóa
```

- `ConnectivityManager.registerDefaultNetworkCallback` phát hiện thay đổi mạng.
- Một `ScheduledExecutorService` đơn luồng tránh các lượt đồng bộ song song.
- Tự đồng bộ khi mạng trở lại, sau khi dữ liệu đổi, khi mở/quay lại app; kiểm tra định kỳ 30 giây khi app đang hoạt động.
- Tạo/sửa loại được gửi trước món; xóa món trước loại để giữ quan hệ.
- Mỗi bản ghi có ID nghiệp vụ ổn định (`UUID` cho bản ghi mới). Trước POST, dò ID đó trong remote: có thì PUT. Khi POST thành công nhưng mất phản hồi, lần thử lại tìm thấy bản ghi và không tạo bản sao.
- ACK chỉ xóa Outbox khi revision bằng revision đã gửi, bảo vệ chỉnh sửa mới trong lúc HTTP đang chạy.
- Khi kéo dữ liệu, bản ghi local còn pending được ưu tiên. Bản ghi remote hợp lệ được ghi lại qua cùng validation và transaction.
- Xóa remote được đối chiếu qua RemoteMap, rồi xóa local nếu bản ghi không có chỉnh sửa pending.
- Timeout, HTTP lỗi và mất mạng không xóa dữ liệu local/hàng đợi.

**Phạm vi:** đồng bộ khi tiến trình app đang chạy. Sau khi hệ điều hành dừng app/force-stop, đồng bộ tiếp tục khi mở app lại. Không tuyên bố chạy nền vô hạn hoặc xử lý cộng tác nhiều người như một hệ thống production. Quy tắc xung đột: pending local thắng trong lượt đồng bộ; sau đó dữ liệu remote được kéo lại.

## 8. Kiểm thử

`FoodInstrumentation` dùng các API Android chuẩn, không cần thư viện test ngoài. Database thử nghiệm là `BIT240253_Tests.db`, tách khỏi dữ liệu ứng dụng.

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w vn.edu.bit240253.foodmanager.test/vn.edu.bit240253.foodmanager.FoodInstrumentation
```

Kết quả đã chạy: **PASS: 62 checks on Android SQLite**. Bao gồm seed, tìm/lọc phối hợp, sort, validation, khóa ngoại, ranh giới 15/16 phút, thay ảnh đại diện, danh sách ảnh rỗng, outbox gộp thay đổi, ACK cũ, đọc lại sau đóng/mở DB và rà soát tiền tố từng bảng/cột. `lintDebug` không còn lỗi; còn cảnh báo về API/SDK và cách dùng chuỗi dịch thủ công, được ghi trong báo cáo lint.

Minh chứng đồng bộ thật và thao tác UI nằm ở các video, ảnh màn hình và file JSON bàn giao riêng. Không dùng REST server local thay thế MockAPI trong phần minh chứng.

## 9. Hai video và quy định nộp

- **Video 1:** quay màn hình ứng dụng Android trên emulator; có danh tính, danh sách, chi tiết, thêm/sửa/xóa, bộ lọc, ảnh, đổi ngôn ngữ, tắt mạng và tự đồng bộ với MockAPI.
- **Video 2:** quay cửa sổ trình bày mã nguồn thật, kèm thuyết minh tiếng Việt bằng **giọng tổng hợp**, không giả giọng sinh viên. Theo yêu cầu sinh viên, Video 2 và tệp phụ của Video 2 không tải lên kho này; phần nộp đó được xử lý riêng trên LMS.
- Đề cho phép AI hỗ trợ nhưng yêu cầu sinh viên hiểu và giải thích được code. Hãy đọc mục 10 và đối chiếu mã nguồn trước buổi demo.
- Theo đề: mã nguồn/link Git và Video 1 nộp ngay trong ca kiểm tra; Video 2 chậm nhất **23/10/2026**. Việc tải lên LMS do sinh viên thực hiện.

## 10. Câu hỏi cần hiểu khi bảo vệ

| Câu hỏi | Ý chính cần giải thích |
|---|---|
| Vì sao chọn SQLite? | Lưu quan hệ, CHECK/UNIQUE/FK, transaction; CRUD không phụ thuộc Internet. |
| Tại sao có DishImage? | Quan hệ một món nhiều ảnh, lưu thứ tự và một ảnh đại diện. |
| Làm sao không xóa được loại đang dùng? | Kiểm tra ở FoodDb và FK RESTRICT ở SQLite. |
| Ngăn trùng tên thế nào? | Chuẩn hóa Unicode/trim/lowercase; unique theo loại + tên chuẩn hóa. |
| Tại sao cần Outbox? | Ghi dữ liệu và ý định gửi trong cùng transaction; không mất thay đổi khi offline. |
| Vì sao không trùng khi mạng chập chờn? | Dò ID nghiệp vụ ổn định trước POST; retry dùng PUT nếu đã có. |
| Revision để làm gì? | ACK của lần gửi cũ không xóa thay đổi mới. |
| Tự đồng bộ phát hiện mạng bằng gì? | ConnectivityManager NetworkCallback, cộng lịch thử lại và lifecycle onResume. |
| Đa ngôn ngữ ở đâu? | t(en,vi), biến vi và SharedPreferences; form giữ draft khi dựng lại. |
| Thời gian 15 phút thuộc nhóm nào? | Quick/Nhanh vì điều kiện ≤ 15. |
| Tiền tố MSSV ở đâu? | settings.gradle; Schema.MSSV/PREFIX/f(); SQL và payload JSON; schema MockAPI RemoteId. |
| Khi API lỗi thì sao? | Không chặn CRUD, giữ Outbox, hiển thị trạng thái và retry tự động. |

## 11. Tài liệu chính thức tham khảo

- [Android – SQLite](https://developer.android.com/training/data-storage/sqlite).
- [Android – đọc trạng thái mạng và callback](https://developer.android.com/develop/connectivity/network-ops/reading-network-state).
- [MockAPI documentation](https://github.com/mockapi-io/docs/wiki).

Các nguồn dùng để đối chiếu API kỹ thuật; mã nguồn được viết cho bài này, không lấy mã sinh viên khác.



## Hồ sơ nộp bài trên GitHub

Kho nộp bài: https://github.com/vanviet2k6-creator/BaiKiemKtraGiuaKi

Project Android Studio nằm tại thư mục gốc. Tên project trong `settings.gradle` vẫn là `BIT240253_FoodManager`, đúng mẫu đề bài.

| Tệp/thư mục | Nội dung |
|---|---|
| `submission/Video_1_BIT240253_Demo_ung_dung.mp4` | Video 1 quay ứng dụng trên emulator, khoảng 3 phút 42 giây, gồm minh chứng offline và tự đồng bộ thật |
| `submission/BIT240253_FoodManager.apk` | APK debug để cài và kiểm tra |
| `submission/Bao_cao_BIT240253_FoodManager.pdf` | Báo cáo chi tiết, mô hình dữ liệu, chức năng, validation và đồng bộ |
| `submission/BIT240253_FoodManager_Source.zip` | Mã nguồn nén, không gồm APK/video/build cache |
| `submission/HUONG_DAN_NOP_BAI.md` | Cách tải, mở project, xem video và nộp LMS |
| `submission/SHA256SUMS.txt` | Mã SHA-256 đối chiếu các tệp nộp |
| `docs/` | Minh chứng API, ảnh chụp ứng dụng và kết quả 62 kiểm tra SQLite |

**Video 2 cùng kịch bản, mục lục, ảnh preview và âm thanh liên quan không được đưa vào kho, tệp ZIP hoặc lịch sử commit của kho này.**

Đề yêu cầu nộp link Git và Video 1 lên LMS trong ca kiểm tra; Video 2 nộp riêng trên LMS chậm nhất 23/10/2026. Việc đưa tệp lên GitHub chưa thay cho nộp LMS.
