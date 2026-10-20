package vn.edu.bit240253.foodmanager;

import static vn.edu.bit240253.foodmanager.Schema.*;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

/** Mai Van Viet - BIT240253. Native Android UI; no browser/web-view implementation. */
public class MainActivity extends Activity {
  private FoodDb db;
  private SyncManager sync;
  private SharedPreferences prefs;
  private LinearLayout root, body, results;
  private TextView status;
  private boolean vi = false;
  private String page = "list", selectedId = "";
  private String query = "", filterCategory = "", minPrice = "", maxPrice = "";
  private int availability = 0, sort = 0;
  private boolean filtersExpanded = false;
  private final int GREEN = Color.rgb(20, 90, 66),
      INK = Color.rgb(25, 47, 39),
      MUTED = Color.rgb(100, 117, 108),
      BG = Color.rgb(246, 248, 242),
      LINE = Color.rgb(219, 229, 218);

  private int dp(float n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private String t(String en, String vn) {
    return vi ? vn : en;
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs = getSharedPreferences(PREFIX + "Settings", 0);
    vi = prefs.getBoolean(PREFIX + "Vietnamese", false);
    if (state != null) {
      query = state.getString("query", "");
      filterCategory = state.getString("category", "");
      minPrice = state.getString("min", "");
      maxPrice = state.getString("max", "");
      availability = state.getInt("availability");
      sort = state.getInt("sort");
      filtersExpanded = state.getBoolean("filters");
    }
    getWindow().setStatusBarColor(GREEN);
    getWindow().setNavigationBarColor(BG);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    db = new FoodDb(this);
    if (!prefs.getBoolean(PREFIX + "Seeded", false)) {
      db.seed();
      prefs.edit().putBoolean(PREFIX + "Seeded", true).apply();
    }
    sync = new SyncManager(this, db);
    sync.onState =
        () ->
            runOnUiThread(
                () -> {
                  updateStatus();
                  if (page.equals("list") && results != null && !isFinishing()) renderResults();
                  if (sync.state.equals("synced") && root != null) reloadMissingImages(root);
                });
    showList();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (sync != null) sync.request();
  }

  @Override
  protected void onDestroy() {
    sync.onState = () -> {};
    sync.close();
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("query", query);
    out.putString("category", filterCategory);
    out.putString("min", minPrice);
    out.putString("max", maxPrice);
    out.putInt("availability", availability);
    out.putInt("sort", sort);
    out.putBoolean("filters", filtersExpanded);
  }

  private GradientDrawable shape(int color, int stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(14));
    if (stroke != 0) d.setStroke(dp(1), stroke);
    return d;
  }

  private LinearLayout col() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private TextView text(String value, int size, int color, boolean bold) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setLineSpacing(dp(3), 1);
    if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return v;
  }

  private TextView note(String value) {
    TextView v = text(value, 13, MUTED, false);
    v.setPadding(0, dp(5), 0, dp(9));
    return v;
  }

  private Button button(String title, Runnable click) {
    Button b = new Button(this);
    b.setText(title);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTextColor(GREEN);
    b.setMinHeight(dp(44));
    b.setBackground(shape(Color.WHITE, LINE));
    b.setPadding(dp(12), dp(6), dp(12), dp(6));
    b.setOnClickListener(v -> click.run());
    return b;
  }

  private Button primary(String title, Runnable click) {
    Button b = button(title, click);
    b.setTextColor(Color.WHITE);
    b.setBackground(shape(GREEN, 0));
    return b;
  }

  private void gap(LinearLayout parent, int height) {
    View v = new View(this);
    parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
  }

  private void weighted(LinearLayout parent, View v) {
    LinearLayout.LayoutParams p =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    p.setMargins(dp(3), dp(3), dp(3), dp(3));
    parent.addView(v, p);
  }

  private LinearLayout card() {
    LinearLayout c = col();
    c.setPadding(dp(14), dp(14), dp(14), dp(14));
    c.setBackground(shape(Color.WHITE, LINE));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(12);
    c.setLayoutParams(p);
    return c;
  }

  private void base(String title, String subtitle, boolean back, boolean languageButton) {
    root = col();
    root.setBackgroundColor(BG);
    root.setFitsSystemWindows(true);
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          root.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets.consumeSystemWindowInsets();
        });
    LinearLayout head = col();
    head.setPadding(dp(18), dp(12), dp(18), dp(10));
    head.setBackgroundColor(GREEN);
    LinearLayout line = row();
    LinearLayout labels = col();
    labels.addView(text("FOOD MANAGER  /  BIT240253", 11, Color.rgb(195, 224, 206), true));
    labels.addView(text(title, 26, Color.WHITE, true));
    line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    if (languageButton) {
      Button lang =
          button(
              vi ? "EN" : "VI",
              () -> {
                vi = !vi;
                prefs.edit().putBoolean(PREFIX + "Vietnamese", vi).apply();
                if (page.equals("detail")) showDetail(selectedId);
                else if (page.equals("categories")) showCategories();
                else if (page.equals("settings")) showSettings();
                else showList();
              });
      line.addView(lang, new LinearLayout.LayoutParams(dp(52), dp(44)));
    }
    head.addView(line);
    head.addView(text(subtitle, 12, Color.WHITE, false));
    root.addView(head);
    status = text("", 11, GREEN, false);
    status.setPadding(dp(18), dp(8), dp(18), dp(8));
    root.addView(status);
    updateStatus();
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = col();
    body.setPadding(dp(16), dp(8), dp(16), dp(20));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (back) {
      body.addView(button(t("‹ Back", "‹ Quay lại"), () -> showList()));
      gap(body, 12);
    }
  }

  private void updateStatus() {
    if (status == null || sync == null) return;
    String label =
        switch (sync.state) {
          case "syncing" -> t("Syncing automatically…", "Đang tự đồng bộ…");
          case "synced" -> t("Cloud up to date", "Đã đồng bộ lên cloud");
          case "offline" ->
              t("Offline · Changes saved on this device", "Ngoại tuyến · Thay đổi đã lưu trên máy");
          case "error" ->
              t(
                  "API unavailable · Local data is safe; will retry",
                  "API chưa truy cập được · Đã lưu local; sẽ thử lại");
          default -> t("Local storage ready", "Lưu trữ local sẵn sàng");
        };
    status.setText(
        label
            + "  •  "
            + db.pendingCount()
            + t(" pending", " chờ gửi")
            + (sync.lastSuccess.isEmpty() ? "" : "  •  " + sync.lastSuccess));
  }

  private EditText input(LinearLayout parent, String label, String value, int type) {
    parent.addView(text(label, 13, INK, true));
    EditText e = new EditText(this);
    e.setSingleLine(true);
    e.setTextSize(15);
    e.setTextColor(INK);
    e.setInputType(type);
    e.setText(value);
    e.setSelectAllOnFocus(true);
    e.setContentDescription(label);
    e.setPadding(dp(12), dp(8), dp(12), dp(8));
    e.setBackground(shape(Color.WHITE, LINE));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(46));
    p.topMargin = dp(5);
    p.bottomMargin = dp(12);
    parent.addView(e, p);
    return e;
  }

  private Spinner spinner(
      LinearLayout parent,
      String label,
      List<String> values,
      int selected,
      java.util.function.IntConsumer changed) {
    parent.addView(text(label, 13, INK, true));
    Spinner s = new Spinner(this);
    ArrayAdapter<String> a =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
    s.setAdapter(a);
    s.setSelection(Math.max(0, Math.min(selected, values.size() - 1)));
    parent.addView(s, new LinearLayout.LayoutParams(-1, dp(48)));
    s.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int n, long id) {
            changed.accept(n);
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    return s;
  }

  private void watch(EditText e, java.util.function.Consumer<String> c) {
    e.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int count, int after) {}

          public void onTextChanged(CharSequence s, int a, int before, int count) {
            c.accept(s.toString());
          }

          public void afterTextChanged(Editable e) {}
        });
  }

  private void hideKeyboard() {
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(root.getWindowToken(), 0);
    root.clearFocus();
  }

  private Double moneyFilter(String s) {
    if (s.isBlank()) return null;
    try {
      double d = Double.parseDouble(s);
      if (!Double.isFinite(d) || d < 0) throw new Exception();
      return d;
    } catch (Exception e) {
      throw new FoodDb.Validation("range");
    }
  }

  private String price(double n) {
    return String.format(Locale.US, "%,.0f ₫", n);
  }

  private String catName(String id) {
    DishCategory c = db.category(id);
    return c == null ? t("Unknown category", "Loại không tồn tại") : c.name;
  }

  private void showList() {
    page = "list";
    results = null;
    base(t("Your menu", "Thực đơn của bạn"), "Mai Van Viet · BIT240253_FoodManager", false, true);
    LinearLayout nav = row();
    weighted(nav, primary(t("+ Add dish", "+ Thêm món"), () -> showForm(null)));
    weighted(nav, button(t("Categories", "Loại món"), this::showCategories));
    weighted(nav, button(t("Settings", "Cài đặt"), this::showSettings));
    body.addView(nav);
    gap(body, 12);
    EditText search = input(body, t("Search dishes", "Tìm tên món"), query, 1);
    search.setHint(t("Type part of a name…", "Nhập một phần tên…"));
    watch(
        search,
        s -> {
          query = s;
          renderResults();
        });
    body.addView(
        button(
            (filtersExpanded ? "− " : "+ ") + t("Filters & sorting", "Lọc & sắp xếp"),
            () -> {
              hideKeyboard();
              filtersExpanded = !filtersExpanded;
              showList();
            }));
    gap(body, 10);
    if (filtersExpanded) {
      LinearLayout filters = card();
      List<DishCategory> cats = db.categories();
      List<String> names = new ArrayList<>();
      names.add(t("All categories", "Tất cả loại"));
      int sel = 0;
      for (int i = 0; i < cats.size(); i++) {
        names.add(cats.get(i).name);
        if (cats.get(i).id.equals(filterCategory)) sel = i + 1;
      }
      if (sel == 0) filterCategory = "";
      spinner(
          filters,
          t("Category", "Loại món"),
          names,
          sel,
          n -> {
            filterCategory = n == 0 ? "" : cats.get(n - 1).id;
            renderResults();
          });
      spinner(
          filters,
          t("Availability", "Trạng thái"),
          Arrays.asList(
              t("All statuses", "Tất cả trạng thái"),
              t("Available only", "Chỉ đang bán"),
              t("Unavailable only", "Chỉ ngừng bán")),
          availability,
          n -> {
            availability = n;
            renderResults();
          });
      LinearLayout prices = row();
      LinearLayout lo = col(), hi = col();
      weighted(prices, lo);
      weighted(prices, hi);
      EditText min = input(lo, t("Min price (VND)", "Giá từ (VND)"), minPrice, 8194);
      EditText max = input(hi, t("Max price (VND)", "Giá đến (VND)"), maxPrice, 8194);
      filters.addView(prices);
      watch(
          min,
          s -> {
            minPrice = s;
            renderResults();
          });
      watch(
          max,
          s -> {
            maxPrice = s;
            renderResults();
          });
      spinner(
          filters,
          t("Sort by", "Sắp xếp theo"),
          Arrays.asList(
              t("Name A–Z", "Tên A–Z"),
              t("Price: low to high", "Giá tăng dần"),
              t("Price: high to low", "Giá giảm dần"),
              t("Preparation time: shortest", "Chế biến nhanh nhất")),
          sort,
          n -> {
            sort = n;
            renderResults();
          });
      filters.addView(
          button(
              t("Reset filters", "Xóa bộ lọc"),
              () -> {
                query = "";
                filterCategory = "";
                minPrice = "";
                maxPrice = "";
                availability = 0;
                sort = 0;
                showList();
              }));
      body.addView(filters);
    }
    results = col();
    body.addView(results);
    renderResults();
    root.setFocusableInTouchMode(true);
    root.requestFocus();
  }

  private void renderResults() {
    if (results == null || !page.equals("list")) return;
    results.removeAllViews();
    try {
      List<Dish> list =
          db.dishes(
              query,
              filterCategory,
              availability,
              moneyFilter(minPrice),
              moneyFilter(maxPrice),
              sort);
      results.addView(note(list.size() + t(" dishes · saved locally", " món · đã lưu local")));
      if (list.isEmpty()) {
        results.addView(text(t("No dishes found", "Không tìm thấy món"), 20, INK, true));
        results.addView(
            note(
                t(
                    "Try another search or clear your filters.",
                    "Hãy đổi từ khóa hoặc xóa bộ lọc.")));
      }
      for (Dish d : list) {
        LinearLayout card = card(), line = row();
        ImageView image = photo(d.thumbnail(), 80);
        line.addView(image, new LinearLayout.LayoutParams(dp(80), dp(80)));
        LinearLayout desc = col();
        desc.setPadding(dp(12), 0, 0, 0);
        desc.addView(text(d.name, 18, INK, true));
        desc.addView(text(catName(d.dishCategoryId), 12, MUTED, false));
        desc.addView(
            text(
                price(d.price) + "  ·  " + d.preparationTime + t(" min", " phút"),
                14,
                GREEN,
                true));
        desc.addView(
            text(
                (d.isAvailable ? t("Available", "Đang bán") : t("Unavailable", "Ngừng bán"))
                    + "  •  "
                    + (d.isQuick() ? t("Quick", "Nhanh") : t("Regular", "Thông thường")),
                12,
                d.isAvailable ? GREEN : MUTED,
                false));
        line.addView(desc, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(line);
        card.setOnClickListener(v -> showDetail(d.id));
        card.setContentDescription(t("Open dish ", "Mở món ") + d.name);
        results.addView(card);
      }
    } catch (FoodDb.Validation e) {
      results.addView(text(error(e.code), 14, Color.rgb(170, 50, 40), false));
    }
  }

  private ImageView photo(String url, int h) {
    ImageView v = new ImageView(this);
    v.setBackground(shape(Color.rgb(224, 235, 219), 0));
    v.setClipToOutline(true);
    v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    v.setImageResource(vn.edu.bit240253.foodmanager.R.drawable.ic_food);
    v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(h)));
    Images.load(v, url);
    return v;
  }

  private void reloadMissingImages(View view) {
    if (view instanceof ImageView image
        && !(image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable)
        && image.getTag() instanceof String url
        && !url.isEmpty()) Images.load(image, url);
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) reloadMissingImages(group.getChildAt(i));
  }

  private void showDetail(String id) {
    hideKeyboard();
    page = "detail";
    selectedId = id;
    Dish d = db.dish(id);
    if (d == null) {
      alert(error("missing"));
      showList();
      return;
    }
    base(t("Dish details", "Chi tiết món"), "Mai Van Viet · BIT240253", true, true);
    if (d.images.isEmpty()) {
      body.addView(photo("", 170));
      body.addView(note(t("No images yet", "Chưa có ảnh")));
    } else {
      body.addView(photo(d.thumbnail(), 190));
      body.addView(
          note(t("Cover photo · ", "Ảnh đại diện · ") + d.images.size() + t(" images", " ảnh")));
    }
    body.addView(text(d.name, 28, INK, true));
    body.addView(note(catName(d.dishCategoryId)));
    body.addView(text(price(d.price), 25, GREEN, true));
    body.addView(
        note(
            d.preparationTime
                + t(" minutes · ", " phút · ")
                + (d.isQuick()
                    ? t("Quick (≤15 min)", "Nhanh (≤15 phút)")
                    : t("Regular (>15 min)", "Thông thường (>15 phút)"))));
    body.addView(
        text(
            d.isAvailable ? t("Available", "Đang bán") : t("Unavailable", "Ngừng bán"),
            15,
            GREEN,
            true));
    gap(body, 12);
    body.addView(text(t("Description", "Mô tả"), 15, INK, true));
    body.addView(
        note(d.description.isBlank() ? t("No description", "Chưa có mô tả") : d.description));
    LinearLayout actions = row();
    weighted(actions, primary(t("Edit dish", "Sửa món"), () -> showForm(d.id)));
    weighted(actions, button(t("Delete dish", "Xóa món"), () -> confirmDelete(DISH, d.id, d.name)));
    body.addView(actions);
    gap(body, 18);
    body.addView(text(t("Photo gallery", "Thư viện ảnh"), 20, INK, true));
    for (int i = 0; i < d.images.size(); i++) {
      DishImage im = d.images.get(i);
      LinearLayout c = card();
      c.addView(photo(im.imageUrl, 160));
      final int index = i;
      c.addView(
          button(
              im.isThumbnail
                  ? t("✓ Current cover", "✓ Ảnh đại diện hiện tại")
                  : t("Use as cover", "Đặt làm ảnh đại diện"),
              () -> {
                for (int n = 0; n < d.images.size(); n++) d.images.get(n).isThumbnail = n == index;
                attempt(
                    () -> {
                      db.saveDish(d, false);
                      sync.request();
                      showDetail(d.id);
                    });
              }));
      body.addView(c);
    }
    body.addView(note(t("Record ID: ", "Mã bản ghi: ") + d.id));
  }

  private String error(String code) {
    return switch (code) {
      case "name" -> t("Name is required.", "Tên không được để trống.");
      case "price" ->
          t("Price must be a finite number greater than 0.", "Giá phải là số hữu hạn lớn hơn 0.");
      case "time" ->
          t(
              "Preparation time must be a positive whole number.",
              "Thời gian chế biến phải là số nguyên lớn hơn 0.");
      case "category" -> t("Choose a valid stored category.", "Hãy chọn loại món hợp lệ đã lưu.");
      case "duplicate" ->
          t(
              "A dish with this name already exists in this category.",
              "Tên món đã tồn tại trong cùng loại.");
      case "range" ->
          t(
              "Invalid price range. Use non-negative numbers; min ≤ max.",
              "Khoảng giá không hợp lệ. Giá không âm; giá từ ≤ giá đến.");
      case "inuse" ->
          t(
              "Cannot delete a category that still contains dishes.",
              "Không thể xóa loại đang có món ăn sử dụng.");
      case "missing" -> t("This record no longer exists.", "Bản ghi này không còn tồn tại.");
      case "url" -> t("Enter a valid HTTPS image URL.", "Nhập URL ảnh HTTPS hợp lệ.");
      case "thumbnail" ->
          t("Only one cover photo is allowed.", "Mỗi món chỉ được có một ảnh đại diện.");
      case "endpoint" ->
          t("Enter a valid HTTPS MockAPI base URL.", "Nhập URL gốc MockAPI HTTPS hợp lệ.");
      default ->
          t(
              "Unable to save. Your existing data has been preserved.",
              "Không thể lưu. Dữ liệu cũ vẫn được giữ nguyên.");
    };
  }

  private void alert(String message) {
    new AlertDialog.Builder(this)
        .setTitle(t("Notice", "Thông báo"))
        .setMessage(message)
        .setPositiveButton(t("OK", "Đóng"), null)
        .show();
  }

  private void attempt(Runnable action) {
    try {
      action.run();
    } catch (FoodDb.Validation e) {
      alert(error(e.code));
    } catch (Exception e) {
      alert(error("storage"));
    }
  }

  private void confirmDelete(String entity, String id, String name) {
    new AlertDialog.Builder(this)
        .setTitle(t("Confirm deletion", "Xác nhận xóa"))
        .setMessage(t("Delete ", "Xóa ") + name + "?")
        .setNegativeButton(t("Cancel", "Hủy"), null)
        .setPositiveButton(
            t("Delete", "Xóa"),
            (a, b) ->
                attempt(
                    () -> {
                      db.delete(entity, id, false);
                      sync.request();
                      if (entity.equals(DISH)) showList();
                      else showCategories();
                    }))
        .show();
  }

  private void showForm(String id) {
    page = "form";
    selectedId = id == null ? "" : id;
    Dish existing = id == null ? null : db.dish(id);
    if (id != null && existing == null) {
      alert(error("missing"));
      showList();
      return;
    }
    Dish draft = existing == null ? new Dish() : existing;
    if (existing == null) {
      draft.id = UUID.randomUUID().toString();
      draft.name = "";
      draft.description = "";
      draft.dishCategoryId = "";
      draft.isAvailable = true;
    }
    base(
        id == null ? t("Add a dish", "Thêm món ăn") : t("Edit dish", "Chỉnh sửa món"),
        "BIT240253_FoodManager",
        false,
        false);
    // Language switch keeps unsaved form data by saving it in the draft before rebuilding.
    renderForm(draft, id == null);
  }

  private void renderForm(Dish d, boolean creating) {
    renderForm(
        d,
        creating,
        d.price == 0
            ? ""
            : java.math.BigDecimal.valueOf(d.price).stripTrailingZeros().toPlainString(),
        d.preparationTime == 0 ? "" : Integer.toString(d.preparationTime));
  }

  private void renderForm(Dish d, boolean creating, String priceText, String timeText) {
    body.removeAllViews();
    LinearLayout top = row();
    weighted(
        top,
        button(
            t("Cancel", "Hủy"),
            () -> {
              hideKeyboard();
              showList();
            }));
    final String[] priceRaw = {priceText}, timeRaw = {timeText};
    Button lang =
        button(
            vi ? "English" : "Tiếng Việt",
            () -> {
              vi = !vi;
              prefs.edit().putBoolean(PREFIX + "Vietnamese", vi).apply();
              base(
                  creating ? t("Add a dish", "Thêm món ăn") : t("Edit dish", "Chỉnh sửa món"),
                  "BIT240253_FoodManager",
                  false,
                  false);
              renderForm(d, creating, priceRaw[0], timeRaw[0]);
            });
    weighted(top, lang);
    body.addView(top);
    gap(body, 12);
    EditText name = input(body, t("Dish name *", "Tên món *"), d.name, 1);
    watch(name, s -> d.name = s);
    EditText price = input(body, t("Price (VND) *", "Giá (VND) *"), priceText, 8194);
    EditText time =
        input(body, t("Preparation time (minutes) *", "Thời gian chế biến (phút) *"), timeText, 2);
    // Preserve even invalid text on language changes instead of silently replacing input.
    watch(
        price,
        s -> {
          priceRaw[0] = s;
          try {
            d.price = Double.parseDouble(s);
          } catch (Exception e) {
            d.price = 0;
          }
        });
    watch(
        time,
        s -> {
          timeRaw[0] = s;
          try {
            d.preparationTime = Integer.parseInt(s);
          } catch (Exception e) {
            d.preparationTime = 0;
          }
        });
    List<DishCategory> cats = db.categories();
    List<String> names = new ArrayList<>();
    int current = 0;
    names.add(t("Select category…", "Chọn loại món…"));
    for (int i = 0; i < cats.size(); i++) {
      names.add(cats.get(i).name);
      if (cats.get(i).id.equals(d.dishCategoryId)) current = i + 1;
    }
    spinner(
        body,
        t("Category *", "Loại món *"),
        names,
        current,
        n -> d.dishCategoryId = n == 0 ? "" : cats.get(n - 1).id);
    Switch available = new Switch(this);
    available.setText(t("Available for sale", "Đang bán"));
    available.setTextColor(INK);
    available.setChecked(d.isAvailable);
    available.setPadding(0, dp(12), 0, dp(12));
    available.setOnCheckedChangeListener((v, checked) -> d.isAvailable = checked);
    body.addView(available);
    EditText desc = input(body, t("Description", "Mô tả"), d.description, 1);
    desc.setSingleLine(false);
    desc.setMinLines(2);
    desc.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(76)));
    watch(desc, s -> d.description = s);
    gap(body, 12);
    body.addView(text(t("Photos", "Ảnh món ăn"), 20, INK, true));
    body.addView(
        note(
            t(
                "Add HTTPS ImageURLs. Select one cover photo; no image is also valid.",
                "Thêm ImageUrl HTTPS. Chọn một ảnh đại diện; có thể không có ảnh.")));
    LinearLayout gallery = col();
    body.addView(gallery);
    renderFormImages(gallery, d);
    EditText url = input(body, t("Image URL", "Đường dẫn ảnh"), "", 17);
    url.setHint("https://…");
    body.addView(
        button(
            t("+ Add image", "+ Thêm ảnh"),
            () -> {
              String u = url.getText().toString().trim();
              if (!FoodDb.validImage(u)) {
                alert(error("url"));
                return;
              }
              d.images.add(new DishImage(u, d.images.isEmpty()));
              url.setText("");
              hideKeyboard();
              renderFormImages(gallery, d);
            }));
    gap(body, 16);
    body.addView(
        primary(
            t("Save dish", "Lưu món ăn"),
            () ->
                attempt(
                    () -> {
                      d.name = name.getText().toString();
                      d.description = desc.getText().toString();
                      try {
                        d.price = Double.parseDouble(priceRaw[0]);
                      } catch (Exception e) {
                        throw new FoodDb.Validation("price");
                      }
                      try {
                        d.preparationTime = Integer.parseInt(timeRaw[0]);
                      } catch (Exception e) {
                        throw new FoodDb.Validation("time");
                      }
                      db.saveDish(d, false);
                      hideKeyboard();
                      sync.request();
                      showDetail(d.id);
                    })));
    root.setFocusableInTouchMode(true);
    root.requestFocus();
  }

  private void renderFormImages(LinearLayout parent, Dish d) {
    parent.removeAllViews();
    if (d.images.isEmpty()) parent.addView(note(t("No images yet", "Chưa có ảnh")));
    for (int i = 0; i < d.images.size(); i++) {
      DishImage im = d.images.get(i);
      LinearLayout c = card();
      final int idx = i;
      c.addView(photo(im.imageUrl, 120));
      LinearLayout buttons = row();
      weighted(
          buttons,
          button(
              im.isThumbnail ? t("✓ Cover", "✓ Đại diện") : t("Set cover", "Đặt đại diện"),
              () -> {
                for (int n = 0; n < d.images.size(); n++) d.images.get(n).isThumbnail = n == idx;
                renderFormImages(parent, d);
              }));
      weighted(
          buttons,
          button(
              t("Remove", "Bỏ ảnh"),
              () -> {
                d.images.remove(idx);
                if (!d.images.isEmpty() && d.images.stream().noneMatch(p -> p.isThumbnail))
                  d.images.get(0).isThumbnail = true;
                renderFormImages(parent, d);
              }));
      c.addView(buttons);
      parent.addView(c);
    }
  }

  private void showCategories() {
    page = "categories";
    base(t("Categories", "Loại món ăn"), "Mai Van Viet · BIT240253", true, true);
    body.addView(primary(t("+ Add category", "+ Thêm loại"), () -> editCategory(null)));
    gap(body, 12);
    List<DishCategory> list = db.categories();
    if (list.isEmpty())
      body.addView(
          note(
              t(
                  "No categories yet. Add one before creating a dish.",
                  "Chưa có loại món. Hãy thêm loại trước khi tạo món.")));
    for (DishCategory c : list) {
      LinearLayout card = card();
      card.addView(text(c.name, 20, INK, true));
      card.addView(note(c.description));
      card.addView(note(db.dishes("", c.id, 0, null, null, 0).size() + t(" dishes", " món")));
      LinearLayout r = row();
      weighted(r, button(t("Edit", "Sửa"), () -> editCategory(c)));
      weighted(r, button(t("Delete", "Xóa"), () -> confirmDelete(CATEGORY, c.id, c.name)));
      card.addView(r);
      body.addView(card);
    }
  }

  private void editCategory(DishCategory old) {
    LinearLayout form = col();
    form.setPadding(dp(20), dp(10), dp(20), dp(10));
    EditText name = input(form, t("Name *", "Tên *"), old == null ? "" : old.name, 1),
        desc = input(form, t("Description", "Mô tả"), old == null ? "" : old.description, 1);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(
                old == null
                    ? t("New category", "Thêm loại món")
                    : t("Edit category", "Sửa loại món"))
            .setView(form)
            .setNegativeButton(t("Cancel", "Hủy"), null)
            .setPositiveButton(t("Save", "Lưu"), null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    b ->
                        attempt(
                            () -> {
                              db.saveCategory(
                                  new DishCategory(
                                      old == null ? UUID.randomUUID().toString() : old.id,
                                      name.getText().toString(),
                                      desc.getText().toString()),
                                  false);
                              sync.request();
                              dialog.dismiss();
                              showCategories();
                            })));
    dialog.show();
  }

  private void showSettings() {
    page = "settings";
    base(t("Settings & sync", "Cài đặt & đồng bộ"), "BIT240253_FoodManager", true, true);
    LinearLayout identity = card();
    identity.addView(text("Mai Van Viet", 24, INK, true));
    identity.addView(note("BIT240253\nAndroid Native Java · SQLite · MockAPI"));
    body.addView(identity);
    body.addView(text(t("Automatic offline sync", "Tự đồng bộ dữ liệu offline"), 20, INK, true));
    body.addView(
        note(
            t(
                "Every change is saved in SQLite first. When Internet returns, queued changes are"
                    + " uploaded automatically. Remote changes are checked every 30 seconds while"
                    + " the app is open, and on app launch/resume. No sync button is required.",
                "Mọi thay đổi được lưu SQLite trước. Khi có Internet trở lại, ứng dụng tự gửi hàng"
                    + " đợi. Dữ liệu remote được kiểm tra mỗi 30 giây khi mở ứng dụng và khi khởi"
                    + " động/quay lại app. Không cần bấm nút đồng bộ.")));
    EditText endpoint = input(body, t("MockAPI base URL", "URL gốc MockAPI"), sync.baseUrl(), 17);
    body.addView(
        primary(
            t("Save API configuration", "Lưu cấu hình API"),
            () ->
                attempt(
                    () -> {
                      sync.configure(endpoint.getText().toString().trim());
                      hideKeyboard();
                      alert(
                          t(
                              "Configuration saved. Automatic sync is active.",
                              "Đã lưu cấu hình. Tự đồng bộ đang hoạt động."));
                    })));
    gap(body, 14);
    body.addView(note(t("Resources: ", "Các resource: ") + CATEGORY + "\n" + DISH));
    body.addView(note(t("Pending changes: ", "Thay đổi chờ gửi: ") + db.pendingCount()));
    if (!sync.lastError.isEmpty())
      body.addView(note(t("Last API error: ", "Lỗi API gần nhất: ") + sync.lastError));
    body.addView(
        note(
            t(
                "Photos use HTTPS URLs and a local cache. Missing images display a placeholder.\n"
                    + "English is the default language; your selection is remembered.",
                "Ảnh dùng URL HTTPS và bộ nhớ đệm local. Ảnh lỗi hiển thị hình thay thế.\n"
                    + "Tiếng Anh là ngôn ngữ mặc định; lựa chọn của bạn được ghi nhớ.")));
  }

  @Override
  public void onBackPressed() {
    if (!page.equals("list")) showList();
    else super.onBackPressed();
  }
}
