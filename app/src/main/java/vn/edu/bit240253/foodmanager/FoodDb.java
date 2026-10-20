package vn.edu.bit240253.foodmanager;

import static vn.edu.bit240253.foodmanager.Schema.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.Normalizer;
import java.util.*;
import org.json.*;

/** SQLite is the source of truth. Mutations + durable outbox commit in one transaction. */
public class FoodDb extends SQLiteOpenHelper {
  public FoodDb(Context c) {
    this(c, PREFIX + "FoodManager.db");
  }

  FoodDb(Context c, String databaseName) {
    super(c, databaseName, null, 1);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE "
            + CATEGORY
            + " ("
            + f("Id")
            + " TEXT PRIMARY KEY,"
            + f("Name")
            + " TEXT NOT NULL CHECK(length(trim("
            + f("Name")
            + "))>0),"
            + f("Description")
            + " TEXT NOT NULL)");
    db.execSQL(
        "CREATE TABLE "
            + DISH
            + " ("
            + f("Id")
            + " TEXT PRIMARY KEY,"
            + f("Name")
            + " TEXT NOT NULL CHECK(length(trim("
            + f("Name")
            + "))>0),"
            + f("NormalizedName")
            + " TEXT NOT NULL,"
            + f("Price")
            + " REAL NOT NULL CHECK("
            + f("Price")
            + ">0),"
            + f("PreparationTime")
            + " INTEGER NOT NULL CHECK("
            + f("PreparationTime")
            + ">0),"
            + f("IsAvailable")
            + " INTEGER NOT NULL CHECK("
            + f("IsAvailable")
            + " IN (0,1)),"
            + f("Description")
            + " TEXT NOT NULL,"
            + f("DishCategoryId")
            + " TEXT NOT NULL REFERENCES "
            + CATEGORY
            + "("
            + f("Id")
            + ") ON DELETE RESTRICT, UNIQUE("
            + f("DishCategoryId")
            + ","
            + f("NormalizedName")
            + "))");
    db.execSQL(
        "CREATE TABLE "
            + IMAGE
            + " ("
            + f("Id")
            + " TEXT PRIMARY KEY,"
            + f("DishId")
            + " TEXT NOT NULL REFERENCES "
            + DISH
            + "("
            + f("Id")
            + ") ON DELETE CASCADE,"
            + f("ImageUrl")
            + " TEXT NOT NULL,"
            + f("IsThumbnail")
            + " INTEGER NOT NULL CHECK("
            + f("IsThumbnail")
            + " IN (0,1)),"
            + f("Position")
            + " INTEGER NOT NULL)");
    // A partial UNIQUE index protects the invariant even outside the UI.
    db.execSQL(
        "CREATE UNIQUE INDEX "
            + PREFIX
            + "OneThumbnail ON "
            + IMAGE
            + "("
            + f("DishId")
            + ") WHERE "
            + f("IsThumbnail")
            + "=1");
    db.execSQL(
        "CREATE TABLE "
            + OUTBOX
            + " ("
            + f("Entity")
            + " TEXT NOT NULL,"
            + f("Id")
            + " TEXT NOT NULL,"
            + f("Action")
            + " TEXT NOT NULL,"
            + f("Revision")
            + " INTEGER NOT NULL, PRIMARY KEY("
            + f("Entity")
            + ","
            + f("Id")
            + "))");
    db.execSQL(
        "CREATE TABLE "
            + REMOTE
            + " ("
            + f("Entity")
            + " TEXT NOT NULL,"
            + f("Id")
            + " TEXT NOT NULL,"
            + f("RemoteId")
            + " TEXT NOT NULL, PRIMARY KEY("
            + f("Entity")
            + ","
            + f("Id")
            + "))");
  }

  @Override
  public void onUpgrade(SQLiteDatabase d, int a, int b) {
    throw new IllegalStateException("Migration required");
  }

  public static String norm(String s) {
    return Normalizer.normalize(s.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }

  public static class Validation extends RuntimeException {
    public final String code;

    Validation(String code) {
      super(code);
      this.code = code;
    }
  }

  private String s(Cursor c, String field) {
    return c.getString(c.getColumnIndexOrThrow(f(field)));
  }

  private long number(Cursor c, String field) {
    return c.getLong(c.getColumnIndexOrThrow(f(field)));
  }

  public synchronized List<DishCategory> categories() {
    List<DishCategory> list = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(CATEGORY, null, null, null, null, null, f("Name") + " COLLATE NOCASE")) {
      while (c.moveToNext())
        list.add(new DishCategory(s(c, "Id"), s(c, "Name"), s(c, "Description")));
    }
    return list;
  }

  public synchronized DishCategory category(String id) {
    for (DishCategory c : categories()) if (c.id.equals(id)) return c;
    return null;
  }

  public synchronized Dish dish(String id) {
    try (Cursor c =
        getReadableDatabase()
            .query(DISH, null, f("Id") + "=?", new String[] {id}, null, null, null)) {
      if (!c.moveToFirst()) return null;
      Dish d = new Dish();
      d.id = s(c, "Id");
      d.name = s(c, "Name");
      d.description = s(c, "Description");
      d.dishCategoryId = s(c, "DishCategoryId");
      d.price = c.getDouble(c.getColumnIndexOrThrow(f("Price")));
      d.preparationTime = (int) number(c, "PreparationTime");
      d.isAvailable = number(c, "IsAvailable") == 1;
      try (Cursor im =
          getReadableDatabase()
              .query(
                  IMAGE, null, f("DishId") + "=?", new String[] {id}, null, null, f("Position"))) {
        while (im.moveToNext())
          d.images.add(new DishImage(s(im, "ImageUrl"), number(im, "IsThumbnail") == 1));
      }
      return d;
    }
  }

  public synchronized List<Dish> dishes(
      String query, String cat, int available, Double min, Double max, int order) {
    if ((min != null && (!Double.isFinite(min) || min < 0))
        || (max != null && (!Double.isFinite(max) || max < 0))
        || (min != null && max != null && min > max)) throw new Validation("range");
    List<String> a = new ArrayList<>();
    String where = "1=1";
    // instr treats %, _ and quotes as literal search characters.
    if (!query.trim().isEmpty()) {
      where += " AND instr(" + f("NormalizedName") + ",?)>0";
      a.add(norm(query));
    }
    if (cat != null && !cat.isEmpty()) {
      where += " AND " + f("DishCategoryId") + "=?";
      a.add(cat);
    }
    if (available > 0) {
      where += " AND " + f("IsAvailable") + "=?";
      a.add(available == 1 ? "1" : "0");
    }
    if (min != null) {
      where += " AND " + f("Price") + ">=?";
      a.add(min.toString());
    }
    if (max != null) {
      where += " AND " + f("Price") + "<=?";
      a.add(max.toString());
    }
    String sort =
        switch (order) {
          case 1 -> f("Price") + " ASC";
          case 2 -> f("Price") + " DESC";
          case 3 -> f("PreparationTime") + " ASC";
          default -> f("Name") + " COLLATE NOCASE";
        };
    List<Dish> list = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                DISH,
                new String[] {f("Id")},
                where,
                a.toArray(new String[0]),
                null,
                null,
                sort + "," + f("Id"))) {
      while (c.moveToNext()) list.add(dish(s(c, "Id")));
    }
    return list;
  }

  private void enqueue(SQLiteDatabase db, String entity, String id, String action) {
    // Monotonic revision prevents a network ACK from erasing a newer local edit.
    db.execSQL(
        "INSERT INTO "
            + OUTBOX
            + " VALUES(?,?,?,1) ON CONFLICT("
            + f("Entity")
            + ","
            + f("Id")
            + ") DO UPDATE SET "
            + f("Action")
            + "=excluded."
            + f("Action")
            + ","
            + f("Revision")
            + "="
            + f("Revision")
            + "+1",
        new Object[] {entity, id, action});
  }

  public synchronized void saveCategory(DishCategory c, boolean remote) {
    if (c.name == null || c.name.trim().isEmpty()) throw new Validation("name");
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues v = new ContentValues();
      v.put(f("Id"), c.id);
      v.put(f("Name"), c.name.trim());
      v.put(f("Description"), c.description);
      if (db.update(CATEGORY, v, f("Id") + "=?", new String[] {c.id}) == 0)
        db.insertOrThrow(CATEGORY, null, v);
      if (!remote) enqueue(db, CATEGORY, c.id, "upsert");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized void saveDish(Dish d, boolean remote) {
    if (d.name == null || d.name.trim().isEmpty()) throw new Validation("name");
    if (!Double.isFinite(d.price) || d.price <= 0) throw new Validation("price");
    if (d.preparationTime <= 0) throw new Validation("time");
    if (category(d.dishCategoryId) == null) throw new Validation("category");
    for (Dish other : dishes("", "", 0, null, null, 0))
      if (!other.id.equals(d.id)
          && other.dishCategoryId.equals(d.dishCategoryId)
          && norm(other.name).equals(norm(d.name))) throw new Validation("duplicate");
    int thumb = 0;
    for (DishImage image : d.images) {
      if (!validImage(image.imageUrl)) throw new Validation("url");
      if (image.isThumbnail) thumb++;
    }
    if (thumb > 1) throw new Validation("thumbnail");
    if (!d.images.isEmpty() && thumb == 0) d.images.get(0).isThumbnail = true;
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues v = new ContentValues();
      v.put(f("Id"), d.id);
      v.put(f("Name"), d.name.trim());
      v.put(f("NormalizedName"), norm(d.name));
      v.put(f("Price"), d.price);
      v.put(f("PreparationTime"), d.preparationTime);
      v.put(f("IsAvailable"), d.isAvailable ? 1 : 0);
      v.put(f("Description"), d.description);
      v.put(f("DishCategoryId"), d.dishCategoryId);
      if (db.update(DISH, v, f("Id") + "=?", new String[] {d.id}) == 0)
        db.insertOrThrow(DISH, null, v);
      db.delete(IMAGE, f("DishId") + "=?", new String[] {d.id});
      int i = 0;
      for (DishImage image : d.images) {
        ContentValues im = new ContentValues();
        im.put(f("Id"), d.id + "-" + i);
        im.put(f("DishId"), d.id);
        im.put(f("ImageUrl"), image.imageUrl);
        im.put(f("IsThumbnail"), image.isThumbnail ? 1 : 0);
        im.put(f("Position"), i++);
        db.insertOrThrow(IMAGE, null, im);
      }
      if (!remote) enqueue(db, DISH, d.id, "upsert");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public static boolean validImage(String url) {
    try {
      java.net.URI u = new java.net.URI(url);
      return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null;
    } catch (Exception e) {
      return false;
    }
  }

  public synchronized void delete(String entity, String id, boolean remote) {
    if (!entity.equals(CATEGORY) && !entity.equals(DISH)) throw new IllegalArgumentException();
    if (entity.equals(CATEGORY) && !dishes("", id, 0, null, null, 0).isEmpty())
      throw new Validation("inuse");
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      if (db.delete(entity, f("Id") + "=?", new String[] {id}) == 0)
        throw new Validation("missing");
      if (!remote) enqueue(db, entity, id, "delete");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized int pendingCount() {
    try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + OUTBOX, null)) {
      c.moveToFirst();
      return c.getInt(0);
    }
  }

  public static class Change {
    public String entity, id, action;
    public long revision;
  }

  public synchronized List<Change> changes() {
    List<Change> out = new ArrayList<>();
    String sort =
        "CASE WHEN "
            + f("Action")
            + "='delete' THEN CASE WHEN "
            + f("Entity")
            + "='"
            + DISH
            + "' THEN 2 ELSE 3 END ELSE CASE WHEN "
            + f("Entity")
            + "='"
            + CATEGORY
            + "' THEN 0 ELSE 1 END END";
    try (Cursor c = getReadableDatabase().query(OUTBOX, null, null, null, null, null, sort)) {
      while (c.moveToNext()) {
        Change q = new Change();
        q.entity = s(c, "Entity");
        q.id = s(c, "Id");
        q.action = s(c, "Action");
        q.revision = number(c, "Revision");
        out.add(q);
      }
    }
    return out;
  }

  public synchronized boolean dirty(String entity, String id) {
    try (Cursor c =
        getReadableDatabase()
            .query(
                OUTBOX,
                null,
                f("Entity") + "=? AND " + f("Id") + "=?",
                new String[] {entity, id},
                null,
                null,
                null)) {
      return c.moveToFirst();
    }
  }

  public synchronized void ack(Change q, String remoteId) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      if (remoteId != null) map(q.entity, q.id, remoteId);
      db.delete(
          OUTBOX,
          f("Entity") + "=? AND " + f("Id") + "=? AND " + f("Revision") + "=?",
          new String[] {q.entity, q.id, Long.toString(q.revision)});
      if (q.action.equals("delete"))
        db.delete(REMOTE, f("Entity") + "=? AND " + f("Id") + "=?", new String[] {q.entity, q.id});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized void map(String entity, String id, String remoteId) {
    ContentValues v = new ContentValues();
    v.put(f("Entity"), entity);
    v.put(f("Id"), id);
    v.put(f("RemoteId"), remoteId);
    getWritableDatabase().insertWithOnConflict(REMOTE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public synchronized JSONObject payload(String entity, String id) throws JSONException {
    JSONObject j = new JSONObject();
    j.put(f("Id"), id);
    if (entity.equals(CATEGORY)) {
      DishCategory c = category(id);
      if (c == null) return null;
      j.put(f("Name"), c.name);
      j.put(f("Description"), c.description);
    } else {
      Dish d = dish(id);
      if (d == null) return null;
      j.put(f("Name"), d.name);
      j.put(f("Price"), d.price);
      j.put(f("PreparationTime"), d.preparationTime);
      j.put(f("IsAvailable"), d.isAvailable);
      j.put(f("Description"), d.description);
      j.put(f("DishCategoryId"), d.dishCategoryId);
      JSONArray images = new JSONArray();
      for (DishImage im : d.images) {
        JSONObject i = new JSONObject();
        i.put(f("ImageUrl"), im.imageUrl);
        i.put(f("IsThumbnail"), im.isThumbnail);
        images.put(i);
      }
      j.put(f("DishImage"), images);
    }
    return j;
  }

  public synchronized void importRemote(String entity, JSONObject j) throws JSONException {
    String id = j.getString(f("Id"));
    if (id.isBlank() || dirty(entity, id)) return;
    if (entity.equals(CATEGORY))
      saveCategory(
          new DishCategory(id, j.getString(f("Name")), j.optString(f("Description"))), true);
    else {
      Dish d = new Dish();
      d.id = id;
      d.name = j.getString(f("Name"));
      d.price = j.getDouble(f("Price"));
      d.preparationTime = j.getInt(f("PreparationTime"));
      d.isAvailable = j.getBoolean(f("IsAvailable"));
      d.description = j.optString(f("Description"));
      d.dishCategoryId = j.getString(f("DishCategoryId"));
      JSONArray a = j.optJSONArray(f("DishImage"));
      if (a != null)
        for (int i = 0; i < a.length(); i++) {
          JSONObject im = a.getJSONObject(i);
          d.images.add(new DishImage(im.getString(f("ImageUrl")), im.optBoolean(f("IsThumbnail"))));
        }
      saveDish(d, true);
    }
    map(entity, id, j.getString(f("RemoteId"))); // MockAPI's route ID also has the MSSV prefix.
  }

  public synchronized void reconcileDeletes(String entity, Set<String> liveIds) {
    List<String> gone = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(REMOTE, null, f("Entity") + "=?", new String[] {entity}, null, null, null)) {
      while (c.moveToNext()) {
        String id = s(c, "Id");
        if (!liveIds.contains(id) && !dirty(entity, id)) gone.add(id);
      }
    }
    for (String id : gone) {
      try {
        if ((entity.equals(DISH) && dish(id) != null)
            || (entity.equals(CATEGORY) && category(id) != null)) delete(entity, id, true);
        getWritableDatabase()
            .delete(REMOTE, f("Entity") + "=? AND " + f("Id") + "=?", new String[] {entity, id});
      } catch (Validation e) {
        /* Remote category deleted before children: retry on next pass. */
      }
    }
  }

  public synchronized void seed() {
    if (!categories().isEmpty()) return;
    saveCategory(
        new DishCategory("cat-main", "Main dishes", "Rice, noodles and nourishing meals"), false);
    saveCategory(new DishCategory("cat-light", "Light bites", "Fresh starters and salads"), false);
    saveCategory(new DishCategory("cat-drink", "Drinks", "Coffee, tea and fresh drinks"), false);
    String bowl = "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=640&q=80";
    String plate = "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=640&q=80";
    String coffee = "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=640&q=80";
    addSeed(
        "dish-01",
        "Chicken rice",
        55000,
        20,
        true,
        "cat-main",
        "Fragrant rice with grilled chicken and seasonal greens.",
        bowl,
        plate);
    addSeed(
        "dish-02",
        "Beef noodles",
        65000,
        25,
        true,
        "cat-main",
        "Slow simmered broth, rice noodles and tender beef.",
        plate,
        bowl);
    addSeed(
        "dish-03",
        "Garden salad",
        35000,
        10,
        true,
        "cat-light",
        "Crisp vegetables with a light citrus dressing.",
        bowl,
        plate);
    addSeed(
        "dish-04",
        "Spring rolls",
        30000,
        15,
        false,
        "cat-light",
        "Fresh rolls with herbs, vegetables and dipping sauce.",
        plate,
        bowl);
    addSeed(
        "dish-05",
        "Iced coffee",
        25000,
        5,
        true,
        "cat-drink",
        "Vietnamese coffee with condensed milk, served over ice.",
        coffee,
        coffee);
  }

  private void addSeed(
      String id,
      String name,
      double price,
      int time,
      boolean available,
      String cat,
      String description,
      String a,
      String b) {
    Dish d = new Dish();
    d.id = id;
    d.name = name;
    d.price = price;
    d.preparationTime = time;
    d.isAvailable = available;
    d.dishCategoryId = cat;
    d.description = description;
    d.images.add(new DishImage(a, true));
    d.images.add(new DishImage(b, false));
    saveDish(d, false);
  }
}
