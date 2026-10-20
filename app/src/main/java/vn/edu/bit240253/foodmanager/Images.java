package vn.edu.bit240253.foodmanager;

import android.graphics.*;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Download on worker pool, cache locally so previously viewed photos remain usable offline. */
public final class Images {
  private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

  public static void load(ImageView view, String url) {
    view.setTag(url);
    view.setContentDescription(url.isEmpty() ? "—" : url);
    if (url.isEmpty()) return;
    POOL.execute(
        () -> {
          try {
            byte[] hash =
                MessageDigest.getInstance("SHA-256")
                    .digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte b : hash) key.append(String.format("%02x", b));
            File file = new File(view.getContext().getCacheDir(), "BIT240253_" + key + ".jpg");
            byte[] bytes;
            if (file.exists()) bytes = java.nio.file.Files.readAllBytes(file.toPath());
            else {
              HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
              c.setConnectTimeout(8000);
              c.setReadTimeout(10000);
              try (InputStream in = c.getInputStream();
                  ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n, total = 0;
                while ((n = in.read(buf)) != -1) {
                  total += n;
                  if (total > 8 * 1024 * 1024) throw new IOException("Image too large");
                  out.write(buf, 0, n);
                }
                bytes = out.toByteArray();
              } finally {
                c.disconnect();
              }
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            opts.inSampleSize = Math.max(1, Math.max(opts.outWidth, opts.outHeight) / 900);
            opts.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            if (bitmap == null) return;
            if (!file.exists()) java.nio.file.Files.write(file.toPath(), bytes);
            view.post(
                () -> {
                  if (url.equals(view.getTag())) {
                    view.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    view.setImageBitmap(bitmap);
                  }
                });
          } catch (Exception ignored) {
            /* Keep the visible placeholder; malformed/unreachable photos never crash the app. */
          }
        });
  }
}
