package cn.shike.counter;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Resolution-independent material, raised rim or recessed instrument well. */
public final class InstrumentSurface extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final boolean dark, inset;
  private final float radius, density;

  public InstrumentSurface(boolean dark, boolean inset, float radius, float density) {
    this.dark = dark;
    this.inset = inset;
    this.radius = radius * density;
    this.density = density;
  }

  @Override
  public void draw(Canvas canvas) {
    Rect b = getBounds();
    rect.set(b.left, b.top, b.right, b.bottom);
    int[] rim =
        inset
            ? (dark ? new int[] {0xff0d1617, 0xff53635d} : new int[] {0xffb0b6a9, 0xfffdfdf6})
            : (dark ? new int[] {0xff52605a, 0xff192322} : new int[] {0xffffffff, 0xffc6cbbf});
    paint.setShader(new LinearGradient(0, b.top, 0, b.bottom, rim, null, Shader.TileMode.CLAMP));
    canvas.drawRoundRect(rect, radius, radius, paint);
    rect.inset(density * 1.4f, density * 1.4f);
    int[] fill =
        inset
            ? (dark ? new int[] {0xff172323, 0xff263530} : new int[] {0xffe0e3d7, 0xfff2f4e8})
            : (dark ? new int[] {0xff34413d, 0xff273430} : new int[] {0xfffaf9f2, 0xffeeeee4});
    paint.setShader(new LinearGradient(0, b.top, 0, b.bottom, fill, null, Shader.TileMode.CLAMP));
    canvas.drawRoundRect(rect, radius - density, radius - density, paint);
    paint.setShader(null);
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
