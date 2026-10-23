package cn.shike.counter;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class InterfaceTest {
  @Test
  @Config(sdk = {26, 29, 34})
  public void systemInsetsAndCustomSwitchWorkAcrossAndroidVersions() {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      measure(a, 393, 873);
      ViewGroup host = a.findViewById(android.R.id.content);
      View shell = host.getChildAt(0);
      WindowInsets insets = org.robolectric.util.ReflectionHelpers.callConstructor(WindowInsets.class, org.robolectric.util.ReflectionHelpers.ClassParameter.from(Rect.class,new Rect(0,24,0,30)));
      if (android.os.Build.VERSION.SDK_INT >= 30)
        insets =
            new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(), android.graphics.Insets.of(0, 24, 0, 30))
                .build();
      shell.dispatchApplyWindowInsets(insets);
      measure(a, 393, 873);
      assertEquals(24, shell.getPaddingTop());
      assertEquals(30, shell.getPaddingBottom());
      MechanicalSwitch audience = host.findViewWithTag("audience-switch");
      assertEquals("android.widget.Switch", audience.getAccessibilityClassName());
      assertFalse(audience.isChecked());
      audience.performClick();
      assertTrue(((MechanicalSwitch) host.findViewWithTag("audience-switch")).isChecked());
    }
  }

  private void measure(Activity a, int width, int height) {
    View content = a.findViewById(android.R.id.content);
    float density = a.getResources().getDisplayMetrics().density;
    int w = Math.round(width * density), h = Math.round(height * density);
    content.measure(
        View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
    content.layout(0, 0, w, h);
  }

  @Test
  public void navigationAndSwitchesAcrossScreenSizes() {
    for (int[] screen :
        new int[][] {
          {320, 640}, {360, 800}, {393, 873}, {412, 915}, {480, 1000}, {800, 1280}, {800, 400}
        }) {
      RuntimeEnvironment.setQualifiers("w" + screen[0] + "dp-h" + screen[1] + "dp");
      try (ActivityController<MainActivity> c =
          Robolectric.buildActivity(MainActivity.class).setup()) {
        MainActivity a = c.get();
        measure(a, screen[0], screen[1]);
        View content = a.findViewById(android.R.id.content);
        View dock = content.findViewWithTag("navigation-dock");
        assertNotNull(dock);
        assertTrue(dock.getHeight() > 0);
        Rect r = new Rect();
        dock.getGlobalVisibleRect(r);
        assertTrue(
            "bottom clearance " + screen[0] + "x" + screen[1], r.bottom < content.getHeight());
        assertTrue(content.findViewWithTag("nav-计数").isSelected());
        assertTrue(content.findViewWithTag("audience-switch") instanceof MechanicalSwitch);
        content.findViewWithTag("nav-历史").performClick();
        assertTrue(content.findViewWithTag("nav-历史").isSelected());
        assertFalse(content.findViewWithTag("nav-计数").isSelected());
        content.findViewWithTag("nav-分析").performClick();
        assertTrue(content.findViewWithTag("nav-分析").isSelected());
      }
    }
  }

  @Test
  public void startPauseAndClassifiedCountingPreservesSession() {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      measure(a, 393, 873);
      View content = a.findViewById(android.R.id.content);
      ((MechanicalSwitch) content.findViewWithTag("audience-switch")).performClick();
      ((MechanicalSwitch) content.findViewWithTag("start-switch")).performClick();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(220));
      CounterStore store = new CounterStore(a);
      assertNotNull(store.active());
      assertTrue(store.active().typed);
      Button purchase = findButton(content, "已购买\n＋1");
      assertNotNull(purchase);
      purchase.performClick();
      assertEquals(1, store.active().total);
      assertEquals("已购买", store.entries(store.active().id, 0, Long.MAX_VALUE).get(0).kind);
      findButton(content, "暂停").performClick();
      assertEquals("paused", store.active().status);
      assertNotNull(findButton(content, "结束"));
    }
  }

  @Test
  public void largeFontStillHasScrollableControls() {
    RuntimeEnvironment.setQualifiers("w360dp-h640dp");
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      Configuration config = new Configuration(a.getResources().getConfiguration());
      config.fontScale = 1.5f;
      a.getResources().updateConfiguration(config, a.getResources().getDisplayMetrics());
      a.onConfigurationChanged(config);
      measure(a, 360, 640);
      assertNotNull(a.findViewById(android.R.id.content).findViewWithTag("start-switch"));
      assertTrue(hasScroll(a.findViewById(android.R.id.content)));
    }
  }

  private boolean hasScroll(View v) {
    if (v instanceof ScrollView) return true;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        if (hasScroll(((ViewGroup) v).getChildAt(i))) return true;
    return false;
  }

  private Button findButton(View v, String text) {
    if (v instanceof Button && ((Button) v).getText().toString().equals(text)) return (Button) v;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
        Button b = findButton(((ViewGroup) v).getChildAt(i), text);
        if (b != null) return b;
      }
    return null;
  }
}

