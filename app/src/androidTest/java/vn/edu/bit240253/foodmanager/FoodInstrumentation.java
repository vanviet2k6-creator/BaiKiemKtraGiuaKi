package vn.edu.bit240253.foodmanager;

import static vn.edu.bit240253.foodmanager.Schema.*;

import android.app.Instrumentation;
import android.database.Cursor;
import android.os.Bundle;
import java.util.*;

/** Dependency-free device tests against real Android SQLite, in the TEST package's private DB. */
public class FoodInstrumentation extends Instrumentation {
  int passed = 0;

  void check(boolean ok, String name) {
    if (!ok) throw new AssertionError(name);
    passed++;
    android.util.Log.i("BIT240253_TEST", name);
  }

  void invalid(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected " + code);
    } catch (FoodDb.Validation e) {
      check(e.code.equals(code), "validation: " + code);
    }
  }

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    FoodDb db = null;
    try {
      getTargetContext().deleteDatabase(PREFIX + "Tests.db");
      db = new FoodDb(getTargetContext(), PREFIX + "Tests.db");
      final FoodDb store = db;
      db.seed();
      check(db.categories().size() == 3, "3 seed categories");
      check(db.dishes("", "", 0, null, null, 0).size() == 5, "5 seed dishes");
      check(db.dishes("", "", 1, null, null, 0).size() == 4, "unavailable excluded");
      check(db.dishes("", "", 2, null, null, 0).size() == 1, "unavailable retained");
      check(
          db.dishes("a", "cat-light", 1, 30000., 40000., 1).size() == 1,
          "combined search/category/status/price");
      check(db.dishes("", "", 0, null, null, 1).get(0).price == 25000, "price ascending");
      check(db.dishes("", "", 0, null, null, 2).get(0).price == 65000, "price descending");
      check(db.dishes("", "", 0, null, null, 3).get(0).preparationTime == 5, "time ascending");
      check(db.dishes("%", "", 0, null, null, 0).isEmpty(), "literal percent search");
      invalid("range", () -> store.dishes("", "", 0, 50., 10., 0));
      invalid("range", () -> store.dishes("", "", 0, -1., null, 0));
      Dish d = db.dish("dish-01");
      String original = d.name;
      d.name = " ";
      invalid("name", () -> store.saveDish(d, false));
      d.name = original;
      d.price = 0;
      invalid("price", () -> store.saveDish(d, false));
      d.price = Double.NaN;
      invalid("price", () -> store.saveDish(d, false));
      d.price = 55000;
      d.preparationTime = 0;
      invalid("time", () -> store.saveDish(d, false));
      d.preparationTime = 15;
      check(d.isQuick(), "15 minutes quick");
      d.preparationTime = 16;
      check(!d.isQuick(), "16 minutes regular");
      String category = d.dishCategoryId;
      d.dishCategoryId = "missing";
      invalid("category", () -> store.saveDish(d, false));
      d.dishCategoryId = category;
      Dish duplicate = db.dish("dish-02");
      duplicate.name = "  CHICKEN RICE ";
      invalid("duplicate", () -> store.saveDish(duplicate, false));
      duplicate.dishCategoryId = "cat-drink";
      db.saveDish(duplicate, false);
      check(
          db.dish(duplicate.id).name.equals("CHICKEN RICE"),
          "same name allowed in different category");
      invalid("inuse", () -> store.delete(CATEGORY, "cat-main", false));
      check(db.category("cat-main") != null, "category protected");
      check(db.dish("missing") == null, "missing ID safely returns null");
      invalid("missing", () -> store.delete(DISH, "missing", false));
      d.images.get(1).isThumbnail = true;
      invalid("thumbnail", () -> store.saveDish(d, false));
      d.images.get(0).isThumbnail = false;
      db.saveDish(d, false);
      check(db.dish(d.id).images.get(1).isThumbnail, "change cover persists");
      d.images.clear();
      db.saveDish(d, false);
      check(db.dish(d.id).images.isEmpty(), "empty gallery allowed");
      d.images.add(new DishImage("invalid", true));
      invalid("url", () -> store.saveDish(d, false));
      check(db.pendingCount() == 8, "one queued change per record; edits coalesced");
      FoodDb.Change stale =
          db.changes().stream().filter(q -> q.id.equals("dish-01")).findFirst().get();
      d.images.clear();
      d.price = 58000;
      db.saveDish(d, false);
      db.ack(stale, "999");
      check(db.dirty(DISH, d.id), "stale ACK preserves newer edit");
      FoodDb.Change fresh =
          db.changes().stream().filter(q -> q.id.equals("dish-01")).findFirst().get();
      db.ack(fresh, "999");
      check(!db.dirty(DISH, d.id), "current ACK clears queue");
      String payload = db.payload(DISH, d.id).toString();
      check(payload.contains("BIT240253_Price"), "prefixed JSON fields");
      try (Cursor tables =
          db.getReadableDatabase()
              .rawQuery(
                  "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%'"
                      + " AND name NOT LIKE 'sqlite_%'",
                  null)) {
        while (tables.moveToNext()) {
          String table = tables.getString(0);
          check(table.startsWith(PREFIX), "prefixed table: " + table);
          try (Cursor cols =
              db.getReadableDatabase().rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cols.moveToNext())
              check(cols.getString(1).startsWith(PREFIX), "prefixed column: " + cols.getString(1));
          }
        }
      }
      db.close();
      db = new FoodDb(getTargetContext(), PREFIX + "Tests.db");
      check(db.dish("dish-01").price == 58000, "data survives DB reopen");
      db.delete(DISH, "dish-01", false);
      check(db.dish("dish-01") == null, "delete persists");
      result.putString("stream", "PASS: " + passed + " checks on Android SQLite\n");
      finish(-1, result);
    } catch (Throwable e) {
      result.putString(
          "stream",
          "FAIL after "
              + passed
              + " checks: "
              + e
              + "\n"
              + android.util.Log.getStackTraceString(e));
      finish(0, result);
    } finally {
      if (db != null) db.close();
    }
  }
}
