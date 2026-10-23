package cn.shike.counter;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity implements LocationListener {
  private CounterStore store;
  private LinearLayout root, content;
  private final Map<String, Button> navigation = new LinkedHashMap<>();
  private FrameLayout shell;
  private TextView timer, locLabel;
  private int bg, surface, ink, muted, line, accent;
  private boolean dark, typed;
  private String page = "计数";
  private CounterStore.Session detail;
  private final Handler handler = new Handler(android.os.Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final ExecutorService geoWorker = Executors.newSingleThreadExecutor();
  private final Map<String, String> geoCache = new ConcurrentHashMap<>();
  private LocationManager locations;
  private Location latest;
  private String address = "等待定位";
  private boolean foreground = false;
  private long from, to;
  private String bucket = "小时";
  private static final int SAVE = 200;
  private int ticks = 0;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (++ticks % 10 == 0) store.checkpoint();
          if (timer != null) {
            CounterStore.Session s = store.active();
            timer.setText(ExcelExporter.clock(s == null ? 0 : store.duration(s)));
          }
          handler.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    store = new CounterStore(this);
    locations = (LocationManager) getSystemService(LOCATION_SERVICE);
    Calendar c = Calendar.getInstance();
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    from = c.getTimeInMillis();
    to = from + 86400000 - 1;
    show();
    handler.post(tick);
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(3), 0, dp(3));
    return t;
  }

  private GradientDrawable shape(int color, int radius, boolean stroke) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(radius));
    if (stroke) g.setStroke(dp(1), line);
    return g;
  }

  private LinearLayout column() {
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

  private LinearLayout.LayoutParams weight() {
    return new LinearLayout.LayoutParams(0, -1, 1);
  }

  private void space(LinearLayout l, int h) {
    l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h)));
  }

  private LinearLayout panel() {
    LinearLayout l = column();
    l.setPadding(dp(16), dp(12), dp(16), dp(12));
    l.setBackground(
        new InstrumentSurface(dark, false, 20, getResources().getDisplayMetrics().density));
    l.setElevation(dp(2));
    return l;
  }

  private final class PressButton extends Button {
    PressButton() {
      super(MainActivity.this);
    }

    @Override
    public void setPressed(boolean pressed) {
      super.setPressed(pressed);
      animate().cancel();
      if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
        animate()
            .translationY(pressed ? dp(2) : 0)
            .scaleX(pressed ? .985f : 1f)
            .scaleY(pressed ? .985f : 1f)
            .setDuration(pressed ? 65 : 140)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
      } else {
        setTranslationY(pressed ? dp(2) : 0);
      }
      setElevation(dp(pressed ? 1 : 4));
    }
  }

  private Button button(String label, Runnable action, boolean primary) {
    Button b = new PressButton();
    b.setText(label);
    b.setTextSize(15);
    b.setAllCaps(false);
    b.setTextColor(primary ? Color.WHITE : ink);
    b.setMinHeight(dp(52));
    b.setMinimumWidth(0);
    GradientDrawable g =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {
              primary ? Color.rgb(67, 130, 95) : surface, primary ? Color.rgb(35, 83, 61) : bg
            });
    g.setCornerRadius(dp(dark ? 10 : 16));
    g.setStroke(dp(1), primary ? Color.rgb(52, 102, 76) : line);
    b.setBackground(g);
    b.setElevation(dp(4));
    b.setStateListAnimator(null);
    b.setOnClickListener(
        v -> {
          v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
          try {
            action.run();
          } catch (Exception ex) {
            message("操作未完成：" + ex.getMessage());
          }
        });
    return b;
  }

  private void message(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  private void palette() {
    int mode = getPreferences(0).getInt("theme", 0);
    dark =
        mode == 2
            || (mode == 0
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES);
    bg = Color.parseColor(dark ? "#20292B" : "#EDEAE3");
    surface = Color.parseColor(dark ? "#293538" : "#F8F6F0");
    ink = Color.parseColor(dark ? "#E9F0E7" : "#253E36");
    muted = Color.parseColor(dark ? "#A6B8B6" : "#607168");
    line = Color.parseColor(dark ? "#425456" : "#D8D9CF");
    accent = Color.parseColor(dark ? "#A9D49D" : "#32735A");
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | (dark
                    ? 0
                    : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
    if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(Color.TRANSPARENT);
  }

  private void show() {
    if (isFinishing()) return;
    palette();
    timer = null;
    root = column();
    root.setPadding(dp(18), dp(12), dp(18), dp(20));
    root.setBackgroundColor(bg);
    shell = new FrameLayout(this);
    shell.setBackgroundColor(bg);
    int available = getResources().getDisplayMetrics().widthPixels;
    shell.addView(
        root,
        new FrameLayout.LayoutParams(Math.min(available, dp(720)), -1, Gravity.CENTER_HORIZONTAL));
    shell.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          int left, top, right, bottom;
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets safe =
                insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets gesture =
                insets.getInsets(WindowInsets.Type.mandatorySystemGestures());
            left = safe.left;
            top = safe.top;
            right = safe.right;
            bottom = Math.max(safe.bottom, gesture.bottom);
          } else {
            left = insets.getSystemWindowInsetLeft();
            top = insets.getSystemWindowInsetTop();
            right = insets.getSystemWindowInsetRight();
            bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 29)
              bottom = Math.max(bottom, insets.getMandatorySystemGestureInsets().bottom);
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
              android.view.DisplayCutout cutout = insets.getDisplayCutout();
              left = Math.max(left, cutout.getSafeInsetLeft());
              top = Math.max(top, cutout.getSafeInsetTop());
              right = Math.max(right, cutout.getSafeInsetRight());
              bottom = Math.max(bottom, cutout.getSafeInsetBottom());
            }
          }
          view.setPadding(left, top, right, bottom);
          int width = view.getWidth() - left - right;
          if (width > 0) {
            FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) root.getLayoutParams();
            int wanted = Math.min(width, dp(720));
            if (layout.width != wanted) {
              layout.width = wanted;
              root.setLayoutParams(layout);
            }
          }
          return insets;
        });
    setContentView(shell);
    shell.requestApplyInsets();
    LinearLayout header = row();
    LinearLayout title = column();
    title.addView(text(dark ? "COUNTER / FIELD UNIT" : "FIELD COUNTER", 10, muted));
    title.addView(text("拾客", 26, ink));
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    Button theme = button("◐ 主题", this::theme, false);
    header.addView(theme, new LinearLayout.LayoutParams(dp(92), dp(48)));
    root.addView(header);
    space(root, 12);
    content = column();
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    if (page.equals("计数")) live();
    else if (page.equals("历史")) history();
    else analysis();
    space(root, 10);
    LinearLayout nav = row();
    nav.setTag("navigation-dock");
    nav.setPadding(dp(5), dp(5), dp(5), dp(5));
    nav.setBackground(
        new InstrumentSurface(dark, true, 22, getResources().getDisplayMetrics().density));
    navigation.clear();
    for (String name : new String[] {"计数", "历史", "分析"}) {
      Button item = button(name, () -> navigate(name), false);
      item.setTag("nav-" + name);
      item.setMinHeight(dp(48));
      item.setTextSize(14);
      item.setPadding(dp(6), 0, dp(6), 0);
      nav.addView(item, new LinearLayout.LayoutParams(0, dp(50), 1));
      navigation.put(name, item);
    }
    root.addView(nav, new LinearLayout.LayoutParams(-1, dp(60)));
    styleNavigation();
  }

  private void styleNavigation() {
    for (Map.Entry<String, Button> item : navigation.entrySet()) {
      Button button = item.getValue();
      boolean selected = page.equals(item.getKey());
      button.setSelected(selected);
      button.setContentDescription(item.getKey() + (selected ? "，当前页面" : "，切换页面"));
      button.setTextColor(selected ? Color.WHITE : muted);
      if (selected) {
        GradientDrawable active =
            new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xff51836a, 0xff2a5b43});
        active.setCornerRadius(dp(17));
        active.setStroke(dp(1), 0xff729982);
        button.setBackground(active);
        button.setElevation(dp(3));
      } else {
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setElevation(0);
      }
    }
  }

  private void navigate(String target) {
    if (page.equals(target)) return;
    page = target;
    detail = null;
    timer = null;
    content.animate().cancel();
    content.removeAllViews();
    content.setOrientation(LinearLayout.VERTICAL);
    if (page.equals("计数")) live();
    else if (page.equals("历史")) history();
    else analysis();
    styleNavigation();
    if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
      content.setAlpha(.45f);
      content.setTranslationY(dp(7));
      content
          .animate()
          .alpha(1)
          .translationY(0)
          .setDuration(180)
          .setInterpolator(new android.view.animation.DecelerateInterpolator())
          .start();
    } else {
      content.setAlpha(1);
      content.setTranslationY(0);
    }
    shell.requestApplyInsets();
  }

  private void theme() {
    new AlertDialog.Builder(this)
        .setTitle("外观模式")
        .setSingleChoiceItems(
            new String[] {"跟随系统", "奶油机械 · 浅色", "深色仪表 · 深色"},
            getPreferences(0).getInt("theme", 0),
            (d, i) -> {
              getPreferences(0).edit().putInt("theme", i).apply();
              d.dismiss();
              show();
            })
        .setNeutralButton(
            "定位权限设置",
            (d, w) ->
                startActivity(
                    new Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))))
        .show();
  }

  private LinearLayout toggle(
      String label, boolean checked, java.util.function.Consumer<Boolean> change) {
    LinearLayout control = row();
    control.setMinimumHeight(dp(54));
    String[] parts = label.split(" · ", 2);
    LinearLayout captions = column();
    captions.addView(text(parts[0], 14, ink));
    if (parts.length > 1) captions.addView(text(parts[1], 11, muted));
    control.addView(captions, new LinearLayout.LayoutParams(0, -2, 1));
    MechanicalSwitch switchView = new MechanicalSwitch(this, dark);
    switchView.setTag(label.startsWith("开启") ? "start-switch" : "audience-switch");
    switchView.setContentDescription(parts[0]);
    switchView.setChecked(checked);
    switchView.setOnCheckedChangeListener(
        (button, on) -> {
          button.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
          // Give the start switch a visible green / ON frame before replacing its controls.
          if (label.startsWith("开启")
              && on
              && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            switchView.setEnabled(false);
            handler.postDelayed(() -> change.accept(on), 190);
          } else change.accept(on);
        });
    control.addView(switchView, new LinearLayout.LayoutParams(dp(80), dp(52)));
    captions.setOnClickListener(
        v -> {
          if (switchView.isEnabled()) switchView.performClick();
        });
    return control;
  }

  private void live() {
    CounterStore.Session s = store.active();
    if (s != null) typed = s.typed;
    boolean wide =
        getResources().getConfiguration().screenHeightDp < 520
            && getResources().getConfiguration().screenWidthDp >= 600;
    boolean shortScreen =
        !wide
            && (getResources().getConfiguration().screenHeightDp < 700
                || getResources().getConfiguration().fontScale > 1.25f);
    content.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
    LinearLayout body = content;
    if (shortScreen) {
      ScrollView viewport = new ScrollView(this);
      viewport.setFillViewport(true);
      body = column();
      viewport.addView(body);
      content.addView(viewport, new LinearLayout.LayoutParams(-1, -1));
    }
    LinearLayout upper = column();
    body.addView(
        upper,
        shortScreen
            ? new LinearLayout.LayoutParams(
                -1, dp(getResources().getConfiguration().fontScale > 1.25f ? 400 : 290))
            : wide
                ? new LinearLayout.LayoutParams(0, -1, 7)
                : new LinearLayout.LayoutParams(-1, 0, 7));
    LinearLayout meter = panel(), meterRow = row();
    meter.setBackground(
        new InstrumentSurface(dark, true, 20, getResources().getDisplayMetrics().density));
    boolean largeText = getResources().getConfiguration().fontScale > 1.25f;
    if (largeText) meterRow.setOrientation(LinearLayout.VERTICAL);
    LinearLayout count = column();
    count.addView(text("本次累计人数", 12, muted));
    TextView n =
        text(String.format(Locale.ROOT, "%02d", s == null ? 0 : s.total) + " 人", 42, accent);
    count.addView(n);
    meterRow.addView(
        count,
        largeText
            ? new LinearLayout.LayoutParams(-1, -2)
            : new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout time = column();
    time.addView(
        text(
            s == null ? "○ 尚未开始" : s.status.equals("running") ? "● 记录进行中" : "○ 已暂停",
            12,
            s != null && s.status.equals("running") ? accent : muted));
    timer = text(ExcelExporter.clock(s == null ? 0 : store.duration(s)), 22, ink);
    timer.setTypeface(Typeface.MONOSPACE);
    time.addView(timer);
    time.addView(text("有效计时 · 时:分:秒", 11, muted));
    meterRow.addView(time);
    meter.addView(meterRow);
    upper.addView(meter);
    space(upper, 12);
    LinearLayout list = panel();
    list.addView(text("本次明细 · 最新在前", 12, muted));
    ScrollView scroll = new ScrollView(this);
    LinearLayout entries = column();
    scroll.addView(entries);
    list.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    upper.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    if (s == null || s.total == 0) {
      TextView empty = text("＋\n等待第一位来客\n开启后，点击一次记录一人", 17, muted);
      empty.setGravity(Gravity.CENTER);
      empty.setPadding(0, dp(28), 0, dp(28));
      entries.addView(empty);
    } else addEntries(entries, store.entries(s.id, 0, Long.MAX_VALUE));
    locLabel = text("地点：" + address, 11, muted);
    upper.addView(locLabel);
    LinearLayout lower = column();
    lower.setPadding(dp(8), dp(8), dp(8), 0);
    lower.setBackground(
        new InstrumentSurface(dark, false, 20, getResources().getDisplayMetrics().density));
    LinearLayout.LayoutParams lowerParams =
        wide ? new LinearLayout.LayoutParams(0, -1, 3) : new LinearLayout.LayoutParams(-1, 0, 3);
    if (shortScreen) lowerParams = new LinearLayout.LayoutParams(-1, dp(190));
    if (wide) lowerParams.leftMargin = dp(12);
    else lowerParams.topMargin = dp(10);
    body.addView(lower, lowerParams);
    lower.addView(
        toggle(
            "是否记录有效客群",
            typed,
            on -> {
              typed = on;
              store.typed(on);
              show();
            }));
    if (s == null) {
      lower.addView(
          toggle(
              "开启记录 · 秒表同步开始",
              false,
              on -> {
                if (on) {
                  store.start(typed);
                  requestLocation();
                  show();
                }
              }));
    } else {
      LinearLayout actions = row();
      lower.addView(actions, new LinearLayout.LayoutParams(-1, 0, 1));
      if (s.status.equals("paused")) {
        addAction(
            actions,
            "结束",
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("结束本次记录？")
                    .setMessage("将保存明细与有效计时时长，并清空当前列表。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton(
                        "结束并归档",
                        (d, w) -> {
                          store.transition("ended");
                          typed = false;
                          stopLocation();
                          show();
                        })
                    .show(),
            false);
        addAction(
            actions,
            "继续记录",
            () -> {
              store.transition("running");
              requestLocation();
              show();
            },
            true);
      } else {
        addAction(
            actions,
            "暂停",
            () -> {
              store.transition("paused");
              stopLocation();
              show();
            },
            false);
        if (typed) {
          addAction(actions, "已购买\n＋1", () -> record("已购买"), true);
          addAction(actions, "未购买\n＋1", () -> record("未购买"), false);
        } else addAction(actions, "记录 ＋1", () -> record("通用"), true);
      }
    }
  }

  private void addAction(LinearLayout row, String label, Runnable action, boolean primary) {
    Button b = button(label, action, primary);
    LinearLayout.LayoutParams p = weight();
    p.setMargins(dp(4), dp(5), dp(4), dp(8));
    row.addView(b, p);
  }

  private void addEntries(LinearLayout box, List<CounterStore.Entry> entries) {
    int max = Math.min(entries.size(), 300);
    for (int i = 0; i < max; i++) {
      CounterStore.Entry e = entries.get(i);
      LinearLayout r = column();
      r.setPadding(0, dp(8), 0, dp(8));
      r.addView(text(ExcelExporter.date(e.time) + "  +1 人  · " + e.kind, 14, ink));
      r.addView(text(e.address, 12, muted));
      View divider = new View(this);
      divider.setBackgroundColor(line);
      r.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
      box.addView(r);
    }
    if (entries.size() > max)
      box.addView(text("界面显示最近300条，导出包含全部 " + entries.size() + " 条", 12, muted));
  }

  private void record(String kind) {
    Location l = latest;
    if (l != null
        && (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos() > 120_000_000_000L
            || l.getAccuracy() > 200)) l = null;
    long id = store.add(kind, l);
    if (id < 0) return;
    show();
    if (l != null) {
      final Location captured = new Location(l);
      geoWorker.execute(
          () -> {
            String key =
                String.format(
                    Locale.ROOT, "%.4f,%.4f", captured.getLatitude(), captured.getLongitude());
            String cached = geoCache.get(key);
            String result = cached == null ? "地址暂不可用" : cached;
            try {
              if (cached == null && Geocoder.isPresent()) {
                java.util.List<Address> a =
                    new Geocoder(this, Locale.CHINA)
                        .getFromLocation(captured.getLatitude(), captured.getLongitude(), 1);
                if (a != null && !a.isEmpty()) {
                  Address x = a.get(0);
                  String value = x.getAddressLine(0);
                  if (value != null && !value.isEmpty()) result = value;
                }
              }
            } catch (Exception ignored) {
            }
            geoCache.put(key, result);
            store.address(id, result);
            handler.post(
                () -> {
                  if (foreground && page.equals("计数")) show();
                });
          });
    }
  }

  private void range(LinearLayout box) {
    Button f = button("起：" + ExcelExporter.date(from), () -> pick(true), false),
        t = button("止：" + ExcelExporter.date(to), () -> pick(false), false);
    box.addView(f);
    space(box, 6);
    box.addView(t);
    space(box, 8);
  }

  private void pick(boolean start) {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(start ? from : to);
    new DatePickerDialog(
            this,
            (d, y, m, day) -> {
              c.set(y, m, day);
              new TimePickerDialog(
                      this,
                      (v, h, min) -> {
                        c.set(Calendar.HOUR_OF_DAY, h);
                        c.set(Calendar.MINUTE, min);
                        c.set(Calendar.SECOND, start ? 0 : 59);
                        c.set(Calendar.MILLISECOND, start ? 0 : 999);
                        long val = c.getTimeInMillis();
                        if ((start && val > to) || (!start && val < from)) {
                          message("起始时间不能晚于结束时间");
                          return;
                        }
                        if (start) from = val;
                        else to = val;
                        show();
                      },
                      c.get(Calendar.HOUR_OF_DAY),
                      c.get(Calendar.MINUTE),
                      true)
                  .show();
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH))
        .show();
  }

  private LinearLayout scrollPage() {
    ScrollView sc = new ScrollView(this);
    LinearLayout box = column();
    sc.addView(box);
    content.addView(sc, new LinearLayout.LayoutParams(-1, -1));
    return box;
  }

  private void history() {
    LinearLayout box = scrollPage();
    if (detail != null) {
      CounterStore.Session s = detail;
      box.addView(
          button(
              "‹ 返回历史记录",
              () -> {
                detail = null;
                show();
              },
              false));
      box.addView(text("场次 #" + s.id, 23, ink));
      LinearLayout p = panel();
      p.addView(
          text(
              "开始：" + ExcelExporter.date(s.start) + "\n结束：" + ExcelExporter.date(s.end),
              14,
              muted));
      p.addView(text("共 " + s.total + " 人 · 有效计时 " + ExcelExporter.clock(s.elapsed), 18, ink));
      p.addView(
          text(
              "总跨度 " + ExcelExporter.clock(Math.max(0, s.end - s.start)) + " · 暂停不计入有效时长",
              12,
              muted));
      box.addView(p);
      space(box, 10);
      box.addView(
          button(
              "导出本场次 Excel",
              () ->
                  export(
                      Collections.singletonList(s),
                      store.entries(s.id, 0, Long.MAX_VALUE),
                      s.start,
                      s.end),
              true));
      addEntries(box, store.entries(s.id, 0, Long.MAX_VALUE));
      return;
    }
    box.addView(text("历史记录", 23, ink));
    range(box);
    box.addView(text("显示与所选时间相交的已结束场次；点开可查看完整明细", 12, muted));
    List<CounterStore.Session> sessions = store.history(from, to);
    for (CounterStore.Session s : sessions) {
      space(box, 10);
      box.addView(
          button(
              ExcelExporter.date(s.start)
                  + "\n"
                  + s.total
                  + " 人 · 有效计时 "
                  + ExcelExporter.clock(s.elapsed),
              () -> {
                detail = s;
                show();
              },
              false));
    }
    if (sessions.isEmpty()) box.addView(text("暂无已结束的记录", 16, muted));
    space(box, 12);
    box.addView(
        button("导出所选时间段 Excel", () -> export(sessions, endedEntries(sessions), from, to), true));
  }

  private List<CounterStore.Entry> endedEntries(List<CounterStore.Session> sessions) {
    HashSet<Long> ids = new HashSet<>();
    for (CounterStore.Session s : sessions) ids.add(s.id);
    List<CounterStore.Entry> out = new ArrayList<>();
    for (CounterStore.Entry e : store.entries(0, from, to))
      if (ids.contains(e.sessionId)) out.add(e);
    return out;
  }

  private void analysis() {
    LinearLayout box = scrollPage();
    box.addView(text("数据分析", 23, ink));
    range(box);
    List<CounterStore.Entry> es = endedEntries(store.history(from, to));
    int bought = 0, no = 0, generic = 0;
    for (CounterStore.Entry e : es) {
      if (e.kind.equals("已购买")) bought++;
      else if (e.kind.equals("未购买")) no++;
      else generic++;
    }
    LinearLayout p = panel();
    p.addView(text("合计 " + es.size() + " 人", 26, ink));
    p.addView(
        text(
            "购买转化率 "
                + (bought + no == 0
                    ? "—"
                    : String.format(Locale.CHINA, "%.1f%%", 100.0 * bought / (bought + no))),
            16,
            accent));
    p.addView(text("仅统计已结束场次；通用不参与转化率", 12, muted));
    box.addView(p);
    space(box, 12);
    box.addView(text("客流趋势 · 每" + bucket + "新增人数", 18, ink));
    box.addView(
        button(
            "时间粒度：" + bucket,
            () ->
                new AlertDialog.Builder(this)
                    .setItems(
                        new String[] {"分钟", "小时", "天"},
                        (d, i) -> {
                          bucket = new String[] {"分钟", "小时", "天"}[i];
                          show();
                        })
                    .show(),
            false));
    long step = bucket.equals("分钟") ? 60000 : bucket.equals("小时") ? 3600000 : 86400000;
    int bins = (int) Math.min(48, Math.max(1, (to - from) / step + 1));
    long actual = Math.max(step, (long) Math.ceil((to - from + 1) / (double) bins));
    int[] values = new int[bins];
    for (CounterStore.Entry e : es) values[(int) Math.min(bins - 1, (e.time - from) / actual)]++;
    if (actual > step)
      box.addView(text("范围较大，自动合并为 " + Math.round(actual / 60000.0) + " 分钟/点", 12, muted));
    box.addView(
        new ChartView(values, null, from, actual), new LinearLayout.LayoutParams(-1, dp(230)));
    box.addView(text("客群类别人数", 18, ink));
    box.addView(
        new ChartView(new int[] {bought, no, generic}, new String[] {"已购买", "未购买", "通用"}, 0, 0),
        new LinearLayout.LayoutParams(-1, dp(210)));
    if (es.isEmpty()) box.addView(text("当前时间范围暂无记录", 14, muted));
  }

  private void export(
      List<CounterStore.Session> ss, List<CounterStore.Entry> es, long start, long end) {
    if (ss.isEmpty()) {
      message("暂无可导出的已结束场次");
      return;
    }
    worker.execute(
        () -> {
          try {
            File dir = new File(getFilesDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建导出目录");
            File file = new File(dir, "shike-" + System.currentTimeMillis() + ".xlsx");
            ExcelExporter.write(file, ss, es, start, end);
            handler.post(
                () ->
                    new AlertDialog.Builder(this)
                        .setTitle("Excel 已生成")
                        .setMessage(
                            file.getName() + "\n" + es.size() + " 条明细，" + ss.size() + " 个场次")
                        .setPositiveButton("分享 / 微信", (d, w) -> share(file))
                        .setNeutralButton("保存到文件", (d, w) -> save(file))
                        .setNegativeButton("关闭", null)
                        .show());
          } catch (Exception e) {
            handler.post(() -> message("导出失败：" + e.getMessage()));
          }
        });
  }

  private Uri uri(File file) {
    return Uri.parse("content://cn.shike.counter.exports/" + file.getName());
  }

  private void share(File file) {
    Intent i = new Intent(Intent.ACTION_SEND);
    i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    i.putExtra(Intent.EXTRA_STREAM, uri(file));
    i.setClipData(ClipData.newRawUri("拾客 Excel", uri(file)));
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    startActivity(Intent.createChooser(i, "分享 Excel · 选择微信"));
  }

  private File pendingSave;

  private void save(File file) {
    pendingSave = file;
    getPreferences(0).edit().putString("pendingSave", file.getName()).apply();
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    i.putExtra(Intent.EXTRA_TITLE, file.getName());
    startActivityForResult(i, SAVE);
  }

  @Override
  protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    if (req == SAVE && result == RESULT_OK && data != null && data.getData() != null) {
      if (pendingSave == null)
        pendingSave =
            new File(
                new File(getFilesDir(), "exports"), getPreferences(0).getString("pendingSave", ""));
      final File file = pendingSave;
      final Uri target = data.getData();
      worker.execute(
          () -> {
            try (InputStream in = new FileInputStream(file);
                OutputStream out = getContentResolver().openOutputStream(target)) {
              if (out == null) throw new IOException("无法写入目标文件");
              byte[] buffer = new byte[8192];
              int n;
              while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
              handler.post(() -> message("Excel 已保存"));
            } catch (Exception e) {
              handler.post(() -> message("保存失败：" + e.getMessage()));
            }
          });
    }
  }

  private void requestLocation() {
    if (!foreground) return;
    CounterStore.Session s = store.active();
    if (s == null || !s.status.equals("running")) return;
    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
      latest = null;
      address = "未授权定位，可在主题菜单进入权限设置";
      if (locLabel != null) locLabel.setText(address);
      if (getPreferences(0).getBoolean("locationAsked", false)) return;
      getPreferences(0).edit().putBoolean("locationAsked", true).apply();
      requestPermissions(
          new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
          },
          100);
      return;
    }
    try {
      boolean any = false;
      for (String provider :
          new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER})
        if ((!provider.equals(LocationManager.GPS_PROVIDER)
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED)
            && locations.isProviderEnabled(provider)) {
          locations.requestLocationUpdates(provider, 2000, 0, this);
          Location l = locations.getLastKnownLocation(provider);
          if (l != null
              && (latest == null || l.getElapsedRealtimeNanos() > latest.getElapsedRealtimeNanos()))
            latest = l;
          any = true;
        }
      address = any ? "定位中；每条明细单独解析地址" : "系统定位未开启，记录将保留空位置";
    } catch (Exception e) {
      address = "定位不可用，仍可记录人数";
    }
    if (locLabel != null) locLabel.setText(String.format(Locale.CHINA, "地点：%s", address));
  }

  private void stopLocation() {
    try {
      locations.removeUpdates(this);
    } catch (Exception ignored) {
    }
  }

  @Override
  public void onLocationChanged(Location l) {
    latest = l;
    address = "已获取位置 · 精度约 " + Math.round(l.getAccuracy()) + " 米";
    if (locLabel != null) locLabel.setText(String.format(Locale.CHINA, "地点：%s", address));
  }

  @Override
  public void onProviderEnabled(String p) {
    requestLocation();
  }

  @Override
  public void onProviderDisabled(String p) {
    address = "定位信号不可用";
    if (locLabel != null) locLabel.setText(address);
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] ps, int[] grants) {
    super.onRequestPermissionsResult(r, ps, grants);
    if (r == 100) {
      if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
              == PackageManager.PERMISSION_GRANTED
          || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
              == PackageManager.PERMISSION_GRANTED) requestLocation();
      else {
        address = "未授权定位，仍可记录人数";
        show();
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    foreground = true;
    requestLocation();
  }

  @Override
  protected void onPause() {
    store.checkpoint();
    foreground = false;
    stopLocation();
    super.onPause();
  }

  @Override
  public void onConfigurationChanged(Configuration c) {
    super.onConfigurationChanged(c);
    show();
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    worker.shutdown();
    geoWorker.shutdown();
    stopLocation();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (detail != null) {
      detail = null;
      show();
    } else if (!page.equals("计数")) {
      page = "计数";
      show();
    } else super.onBackPressed();
  }

  private final class ChartView extends View {
    final int[] values;
    final String[] labels;
    final String[] tickLabels;
    final int[] tickIndices;
    final int maximum;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    ChartView(int[] values, String[] labels, long start, long step) {
      super(MainActivity.this);
      this.values = values;
      this.labels = labels;
      int max = 1;
      for (int value : values) max = Math.max(max, value);
      maximum = max;
      tickIndices =
          values.length == 1
              ? new int[] {0}
              : values.length == 2
                  ? new int[] {0, 1}
                  : new int[] {0, values.length / 2, values.length - 1};
      tickLabels = new String[tickIndices.length];
      SimpleDateFormat format =
          new SimpleDateFormat(step >= 86400000 ? "MM-dd" : "HH:mm", Locale.CHINA);
      for (int i = 0; i < tickIndices.length; i++)
        tickLabels[i] = format.format(new Date(start + step * tickIndices[i]));
      setContentDescription(labels == null ? "按时间显示新增人数的折线图" : "已购买、未购买、通用人数柱状图");
    }

    private void label(Canvas canvas, String value, float x, float y, int color) {
      paint.setColor(color);
      paint.setTextSize(dp(11));
      canvas.drawText(value, x, y, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      float left = dp(labels == null ? 36 : 62);
      float right = getWidth() - dp(45);
      float top = dp(25), bottom = getHeight() - dp(34);
      if (labels == null) {
        paint.setStrokeWidth(dp(1));
        for (int i = 0; i < 3; i++) {
          float y = bottom - (bottom - top) * i / 2;
          paint.setColor(line);
          canvas.drawLine(left, y, right, y, paint);
          label(
              canvas,
              String.format(Locale.CHINA, "%.1f", maximum * i / 2.0),
              dp(2),
              y + dp(4),
              muted);
        }
        label(canvas, "人数", dp(3), dp(14), muted);
        paint.setStrokeWidth(dp(2));
        float previousX = 0, previousY = 0;
        for (int i = 0; i < values.length; i++) {
          float x =
              values.length == 1
                  ? (left + right) / 2
                  : left + (right - left) * i / (values.length - 1);
          float y = bottom - (bottom - top) * values[i] / maximum;
          paint.setColor(accent);
          if (i > 0) canvas.drawLine(previousX, previousY, x, y, paint);
          canvas.drawCircle(x, y, dp(3), paint);
          previousX = x;
          previousY = y;
        }
        for (int i = 0; i < tickIndices.length; i++) {
          float x =
              values.length == 1
                  ? (left + right) / 2
                  : left + (right - left) * tickIndices[i] / (values.length - 1);
          label(canvas, tickLabels[i], x - dp(15), bottom + dp(22), muted);
        }
      } else {
        for (int i = 0; i < values.length; i++) {
          float y = top + dp(48) * i;
          label(canvas, labels[i], 0, y + dp(16), ink);
          paint.setColor(accent);
          paint.setAlpha(255 - i * 55);
          float length = (right - left) * values[i] / maximum;
          canvas.drawRect(left, y, left + length, y + dp(23), paint);
          paint.setAlpha(255);
          label(canvas, values[i] + " 人", left + length + dp(5), y + dp(16), ink);
        }
        label(canvas, "人数（人）", left, bottom + dp(20), muted);
      }
    }
  }
}
