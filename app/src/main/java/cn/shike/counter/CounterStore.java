package cn.shike.counter;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.*;

/** Durable state transitions: every count and timer transition is committed before UI feedback. */
public final class CounterStore extends SQLiteOpenHelper {
  private final Context context;

  public CounterStore(Context c) {
    super(c, "shike.db", null, 1);
    context = c;
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE sessions(id INTEGER PRIMARY KEY,start INTEGER NOT NULL,end INTEGER,status"
            + " TEXT NOT NULL,elapsed INTEGER NOT NULL DEFAULT 0,anchor INTEGER NOT NULL,boot"
            + " INTEGER NOT NULL,typed INTEGER NOT NULL DEFAULT 0)");
    db.execSQL(
        "CREATE TABLE records(id INTEGER PRIMARY KEY,session_id INTEGER NOT NULL REFERENCES"
            + " sessions(id),time INTEGER NOT NULL,kind TEXT NOT NULL,lat REAL,lon REAL,accuracy"
            + " REAL,location_time INTEGER,address TEXT NOT NULL)");
    db.execSQL("CREATE INDEX record_time ON records(time)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int a, int b) {
    throw new IllegalStateException("Database migration required");
  }

  public static final class Session {
    public long id, start, end, elapsed, anchor, total;
    public int boot;
    public boolean typed;
    public String status;
  }

  public static final class Entry {
    public long id, sessionId, time, locationTime;
    public String kind, address;
    public Double lat, lon, accuracy;
  }

  private int boot() {
    return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
  }

  private Session read(Cursor c) {
    Session s = new Session();
    s.id = c.getLong(0);
    s.start = c.getLong(1);
    s.end = c.getLong(2);
    s.status = c.getString(3);
    s.elapsed = c.getLong(4);
    s.anchor = c.getLong(5);
    s.boot = c.getInt(6);
    s.typed = c.getInt(7) != 0;
    try (Cursor n =
        getReadableDatabase()
            .rawQuery(
                "SELECT COUNT(*) FROM records WHERE session_id=?", new String[] {"" + s.id})) {
      n.moveToFirst();
      s.total = n.getLong(0);
    }
    return s;
  }

  public Session active() {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM sessions WHERE status!='ended' ORDER BY id DESC LIMIT 1", null)) {
      if (!c.moveToFirst()) return null;
      Session s = read(c);
      if (s.status.equals("running")
          && (s.boot != boot() || s.anchor > SystemClock.elapsedRealtime())) {
        ContentValues v = new ContentValues();
        v.put("status", "paused");
        getWritableDatabase().update("sessions", v, "id=?", new String[] {"" + s.id});
        s.status = "paused";
      }
      return s;
    }
  }

  public long duration(Session s) {
    return s.elapsed
        + (s.status.equals("running") ? Math.max(0, SystemClock.elapsedRealtime() - s.anchor) : 0);
  }

  public void checkpoint() {
    Session s = active();
    if (s == null || !s.status.equals("running")) return;
    ContentValues v = new ContentValues();
    v.put("elapsed", duration(s));
    v.put("anchor", SystemClock.elapsedRealtime());
    getWritableDatabase().update("sessions", v, "id=?", new String[] {"" + s.id});
  }

  public void start(boolean typed) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      if (active() != null) throw new IllegalStateException("已有进行中的场次");
      ContentValues v = new ContentValues();
      v.put("start", System.currentTimeMillis());
      v.put("status", "running");
      v.put("anchor", SystemClock.elapsedRealtime());
      v.put("boot", boot());
      v.put("typed", typed ? 1 : 0);
      db.insertOrThrow("sessions", null, v);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void transition(String status) {
    Session s = active();
    if (s == null) return;
    if (status.equals("running") && !s.status.equals("paused")) return;
    ContentValues v = new ContentValues();
    v.put("elapsed", duration(s));
    v.put("anchor", SystemClock.elapsedRealtime());
    v.put("boot", boot());
    v.put("status", status);
    if (status.equals("ended")) v.put("end", System.currentTimeMillis());
    getWritableDatabase().update("sessions", v, "id=?", new String[] {"" + s.id});
  }

  public void typed(boolean typed) {
    Session s = active();
    if (s != null) {
      ContentValues v = new ContentValues();
      v.put("typed", typed ? 1 : 0);
      getWritableDatabase().update("sessions", v, "id=?", new String[] {"" + s.id});
    }
  }

  public long add(String kind, android.location.Location l) {
    Session s = active();
    if (s == null || !s.status.equals("running")) return -1;
    ContentValues v = new ContentValues();
    v.put("session_id", s.id);
    v.put("time", System.currentTimeMillis());
    v.put("kind", kind);
    v.put("address", l == null ? "未获取位置" : "地址解析中");
    if (l != null) {
      v.put("lat", l.getLatitude());
      v.put("lon", l.getLongitude());
      v.put("accuracy", l.getAccuracy());
      v.put("location_time", l.getTime());
    }
    return getWritableDatabase().insertOrThrow("records", null, v);
  }

  public void address(long id, String address) {
    ContentValues v = new ContentValues();
    v.put("address", address);
    getWritableDatabase().update("records", v, "id=?", new String[] {"" + id});
  }

  public List<Entry> entries(long session, long from, long to) {
    List<Entry> out = new ArrayList<>();
    String where = "time>=? AND time<=?";
    ArrayList<String> args = new ArrayList<>(Arrays.asList("" + from, "" + to));
    if (session > 0) {
      where += " AND session_id=?";
      args.add("" + session);
    }
    try (Cursor c =
        getReadableDatabase()
            .query(
                "records",
                null,
                where,
                args.toArray(new String[0]),
                null,
                null,
                "time DESC,id DESC")) {
      while (c.moveToNext()) {
        Entry e = new Entry();
        e.id = c.getLong(0);
        e.sessionId = c.getLong(1);
        e.time = c.getLong(2);
        e.kind = c.getString(3);
        if (!c.isNull(4)) {
          e.lat = c.getDouble(4);
          e.lon = c.getDouble(5);
          e.accuracy = c.getDouble(6);
          e.locationTime = c.getLong(7);
        }
        e.address = c.getString(8);
        out.add(e);
      }
    }
    return out;
  }

  public List<Session> history(long from, long to) {
    List<Session> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM sessions WHERE status='ended' AND start<=? AND end>=? ORDER BY start"
                    + " DESC",
                new String[] {"" + to, "" + from})) {
      while (c.moveToNext()) out.add(read(c));
    }
    return out;
  }
}
