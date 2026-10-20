package vn.edu.bit240253.foodmanager;

import static vn.edu.bit240253.foodmanager.Schema.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Automatic synchronization while app is running, also on launch/resume after process death. */
public final class SyncManager {
  public static final String DEFAULT_URL = "https://6ad7138df71f154ac487b473.mockapi.io/api/v1";
  private final Context context;
  private final FoodDb db;
  private final SharedPreferences preferences;
  private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
  private final ConnectivityManager connectivity;
  private final ConnectivityManager.NetworkCallback callback;
  public volatile String state = "idle", lastError = "", lastSuccess = "";
  public volatile Runnable onState = () -> {};
  private volatile boolean closed;

  public SyncManager(Context context, FoodDb db) {
    this.context = context.getApplicationContext();
    this.db = db;
    preferences = context.getSharedPreferences(PREFIX + "Settings", 0);
    connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    callback =
        new ConnectivityManager.NetworkCallback() {
          @Override
          public void onAvailable(Network n) {
            request();
          }

          @Override
          public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) request();
          }

          @Override
          public void onLost(Network n) {
            state = "offline";
            onState.run();
          }
        };
    connectivity.registerDefaultNetworkCallback(callback);
    worker.scheduleWithFixedDelay(this::sync, 2, 30, TimeUnit.SECONDS);
  }

  public String baseUrl() {
    return preferences.getString(PREFIX + "ApiUrl", DEFAULT_URL);
  }

  public void configure(String url) {
    try {
      URI u = new URI(url);
      if (!"https".equals(u.getScheme())
          || u.getHost() == null
          || !u.getHost().endsWith(".mockapi.io")
          || u.getQuery() != null
          || u.getFragment() != null) throw new Exception();
    } catch (Exception e) {
      throw new FoodDb.Validation("endpoint");
    }
    preferences.edit().putString(PREFIX + "ApiUrl", url.replaceAll("/+$", "")).apply();
    request();
  }

  public boolean online() {
    Network n = connectivity.getActiveNetwork();
    NetworkCapabilities c = connectivity.getNetworkCapabilities(n);
    return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
  }

  public void request() {
    if (!closed) worker.execute(this::sync);
  }

  private void publish(String s) {
    state = s;
    onState.run();
  }

  private String http(String method, String path, JSONObject body) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(baseUrl() + "/" + path).openConnection();
    c.setConnectTimeout(12000);
    c.setReadTimeout(15000);
    c.setRequestMethod(method);
    c.setRequestProperty("Accept", "application/json");
    try {
      if (body != null) {
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        try (OutputStream out = c.getOutputStream()) {
          out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
      }
      int code = c.getResponseCode();
      if (code == 404 && method.equals("DELETE")) return "{}";
      if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
      try (InputStream in = c.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString("UTF-8");
      }
    } finally {
      c.disconnect();
    }
  }

  private JSONArray list(String entity) throws Exception {
    return new JSONArray(http("GET", entity, null));
  }

  private void sync() {
    if (closed) return;
    if (!online()) {
      publish("offline");
      return;
    }
    publish("syncing");
    try {
      // Read authoritative remote lists before each pass. Retry after an uncertain POST
      // discovers the persisted UUID, then PUTs the existing row instead of POSTing twice.
      Map<String, JSONArray> lists = new HashMap<>();
      lists.put(CATEGORY, list(CATEGORY));
      lists.put(DISH, list(DISH));
      for (FoodDb.Change q : db.changes()) {
        JSONArray rows = lists.get(q.entity);
        List<String> matches = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
          JSONObject row = rows.getJSONObject(i);
          if (q.id.equals(row.optString(f("Id")))) matches.add(row.getString(f("RemoteId")));
        }
        if (q.action.equals("delete")) {
          for (String rid : matches) http("DELETE", q.entity + "/" + rid, null);
          db.ack(q, null);
        } else {
          JSONObject payload = db.payload(q.entity, q.id);
          if (payload == null) continue;
          JSONObject saved;
          if (matches.isEmpty()) saved = new JSONObject(http("POST", q.entity, payload));
          else {
            saved = new JSONObject(http("PUT", q.entity + "/" + matches.get(0), payload));
            // Repair accidental duplicates from earlier interrupted clients.
            for (int i = 1; i < matches.size(); i++)
              http("DELETE", q.entity + "/" + matches.get(i), null);
          }
          db.ack(q, saved.getString(f("RemoteId")));
        }
      }
      // Pull in relationship order; pending local mutations always win over a remote read.
      Set<String> cats = new HashSet<>(), dishes = new HashSet<>();
      for (String entity : new String[] {CATEGORY, DISH}) {
        JSONArray rows = list(entity);
        Set<String> live = entity.equals(CATEGORY) ? cats : dishes;
        for (int i = 0; i < rows.length(); i++) {
          JSONObject row = rows.getJSONObject(i);
          String id = row.optString(f("Id"));
          if (id.isBlank()) continue;
          live.add(id);
          db.importRemote(entity, row);
        }
      }
      db.reconcileDeletes(DISH, dishes);
      db.reconcileDeletes(CATEGORY, cats);
      lastError = "";
      lastSuccess = new java.text.SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
      publish(db.pendingCount() == 0 ? "synced" : "pending");
    } catch (Exception e) {
      lastError = e instanceof IOException ? e.getMessage() : e.getClass().getSimpleName();
      publish(online() ? "error" : "offline");
    }
  }

  public void close() {
    closed = true;
    connectivity.unregisterNetworkCallback(callback);
    worker.shutdownNow();
  }
}
