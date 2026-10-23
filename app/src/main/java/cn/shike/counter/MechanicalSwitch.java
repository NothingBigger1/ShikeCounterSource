package cn.shike.counter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;

/** Self-painted recessed switch: identical geometry across Android OEM themes. */
public final class MechanicalSwitch extends CompoundButton {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private boolean dark;
  private float position;
  private ValueAnimator animator;

  public MechanicalSwitch(Context context, boolean dark) {
    super(context);
    this.dark = dark;
    setButtonDrawable(null);
    setBackground(null);
    setMinWidth(px(80));
    setMinHeight(px(52));
    setPadding(0, 0, 0, 0);
    setLayerType(LAYER_TYPE_SOFTWARE, null);
    setFocusable(true);
  }

  private int px(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  @Override
  public CharSequence getAccessibilityClassName() {
    return "android.widget.Switch";
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setCheckable(true);
    info.setChecked(isChecked());
  }

  @Override
  public void setChecked(boolean checked) {
    super.setChecked(checked);
    float target = checked ? 1 : 0;
    if (animator != null) animator.cancel();
    if (isLaidOut() && ValueAnimator.areAnimatorsEnabled()) {
      animator = ValueAnimator.ofFloat(position, target);
      animator.setDuration(180);
      animator.addUpdateListener(
          a -> {
            position = (float) a.getAnimatedValue();
            invalidate();
          });
      animator.start();
    } else {
      position = target;
      invalidate();
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    if (animator != null) animator.cancel();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float left = px(5),
        top = (getHeight() - px(36)) / 2f,
        right = getWidth() - px(5),
        bottom = top + px(36);
    // Bright lower lip, dark upper lip and shaded well make the track visibly recessed.
    rect.set(left, top, right, bottom);
    paint.setShader(
        new LinearGradient(
            0,
            top,
            0,
            bottom,
            dark ? new int[] {0xff111b1d, 0xff53615e} : new int[] {0xffa5afa4, 0xffffffff},
            null,
            Shader.TileMode.CLAMP));
    canvas.drawRoundRect(rect, px(20), px(20), paint);
    rect.inset(px(1.5f), px(1.5f));
    int topColor = position > .5f ? 0xff225b40 : (dark ? 0xff172421 : 0xffb4bdb1);
    int bottomColor = position > .5f ? 0xff4f9d6b : (dark ? 0xff34463d : 0xffd1d6ca);
    paint.setShader(
        new LinearGradient(0, top, 0, bottom, topColor, bottomColor, Shader.TileMode.CLAMP));
    canvas.drawRoundRect(rect, px(18), px(18), paint);
    float radius = px(14),
        cx = left + px(18) + (right - left - px(36)) * position,
        cy = (top + bottom) / 2;
    paint.setShader(null);
    paint.setColor(0xffdfe2db);
    paint.setShadowLayer(px(3), px(1), px(2), 0x65000000);
    canvas.drawCircle(cx, cy, radius, paint);
    paint.clearShadowLayer();
    paint.setShader(
        new LinearGradient(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius,
            new int[] {0xffffffff, 0xfff0f0e9, 0xffc5cdc1},
            null,
            Shader.TileMode.CLAMP));
    canvas.drawCircle(cx, cy, radius - px(.6f), paint);
    paint.setShader(null);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(px(1));
    paint.setColor(0x99ffffff);
    canvas.drawCircle(cx, cy, radius - px(2), paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(position > .5f ? 0xff327351 : 0xff919c8d);
    canvas.drawCircle(cx, cy, px(2), paint);
  }
}
