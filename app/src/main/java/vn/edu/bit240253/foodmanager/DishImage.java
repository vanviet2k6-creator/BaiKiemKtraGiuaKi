package vn.edu.bit240253.foodmanager;

public class DishImage {
  public String imageUrl;
  public boolean isThumbnail;

  public DishImage(String url, boolean thumbnail) {
    imageUrl = url;
    isThumbnail = thumbnail;
  }
}
