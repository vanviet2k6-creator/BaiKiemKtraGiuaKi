package vn.edu.bit240253.foodmanager;

import java.util.ArrayList;
import java.util.List;

public class Dish {
  public String id, name, description, dishCategoryId;
  public double price;
  public int preparationTime;
  public boolean isAvailable;
  public final List<DishImage> images = new ArrayList<>();

  public boolean isQuick() {
    return preparationTime <= 15;
  }

  public String thumbnail() {
    for (DishImage image : images) if (image.isThumbnail) return image.imageUrl;
    return images.isEmpty() ? "" : images.get(0).imageUrl;
  }
}
