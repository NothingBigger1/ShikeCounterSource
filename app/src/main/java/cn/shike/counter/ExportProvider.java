package cn.shike.counter;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only provider restricted to a single export directory and .xlsx filenames. */
public final class ExportProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  private File resolve(Uri uri) throws FileNotFoundException {
    String name = uri.getLastPathSegment();
    if (name == null || !name.matches("shike-[0-9-]+\\.xlsx")) throw new FileNotFoundException();
    File f = new File(new File(getContext().getFilesDir(), "exports"), name);
    if (!f.isFile()) throw new FileNotFoundException();
    return f;
  }

  @Override
  public String getType(Uri u) {
    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  }

  @Override
  public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("read-only");
    return ParcelFileDescriptor.open(resolve(u), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public Cursor query(Uri u, String[] projection, String selection, String[] args, String order) {
    try {
      File f = resolve(u);
      String[] cols =
          projection == null
              ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
              : projection;
      MatrixCursor c = new MatrixCursor(cols);
      Object[] row = new Object[cols.length];
      for (int i = 0; i < cols.length; i++)
        row[i] =
            OpenableColumns.DISPLAY_NAME.equals(cols[i])
                ? f.getName()
                : OpenableColumns.SIZE.equals(cols[i]) ? f.length() : null;
      c.addRow(row);
      return c;
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  @Override
  public Uri insert(Uri u, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri u, ContentValues v, String s, String[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri u, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}
