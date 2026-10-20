package vn.edu.bit240253.foodmanager;

/** Every application table and persisted SQLite column MUST start with BIT240253_. */
public final class Schema {
  public static final String MSSV = "BIT240253";
  public static final String PREFIX = MSSV + "_";
  public static final String CATEGORY = PREFIX + "DishCategory";
  public static final String DISH = PREFIX + "Dish";
  public static final String IMAGE = PREFIX + "DishImage";
  public static final String OUTBOX = PREFIX + "Outbox";
  public static final String REMOTE = PREFIX + "RemoteMap";

  public static String f(String field) {
    return PREFIX + field;
  }

  private Schema() {}
}
