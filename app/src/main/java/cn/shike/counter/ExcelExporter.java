package cn.shike.counter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/** Minimal standards-compliant OOXML workbook; values are inline strings, never formulas. */
public final class ExcelExporter {
  private ExcelExporter() {}

  public static String date(long time) {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(time));
  }

  public static String clock(long ms) {
    long n = ms / 1000;
    return String.format(Locale.ROOT, "%02d:%02d:%02d", n / 3600, n / 60 % 60, n % 60);
  }

  public static String escape(String s) {
    return s == null
        ? ""
        : s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
  }

  private static void zip(ZipOutputStream z, String name, String content) throws IOException {
    z.putNextEntry(new ZipEntry(name));
    z.write(content.getBytes(StandardCharsets.UTF_8));
    z.closeEntry();
  }

  private static String sheet(List<String[]> rows) {
    StringBuilder b =
        new StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet"
                + " xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView"
                + " workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\""
                + " state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"16\""
                + " width=\"24\" customWidth=\"1\"/></cols><sheetData>");
    int rn = 0;
    for (String[] row : rows) {
      b.append("<row r=\"").append(++rn).append("\">");
      for (int i = 0; i < row.length; i++) {
        String col = "";
        int n = i + 1;
        while (n > 0) {
          n--;
          col = (char) ('A' + n % 26) + col;
          n /= 26;
        }
        b.append("<c r=\"")
            .append(col)
            .append(rn)
            .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
            .append(escape(row[i]))
            .append("</t></is></c>");
      }
      b.append("</row>");
    }
    return b.append("</sheetData></worksheet>").toString();
  }

  public static void write(
      File file,
      List<CounterStore.Session> sessions,
      List<CounterStore.Entry> entries,
      long from,
      long to)
      throws IOException {
    List<String[]> detail = new ArrayList<>(), summary = new ArrayList<>();
    detail.add(
        new String[] {
          "明细ID",
          "场次ID",
          "记录时间（手机本地时区）",
          "人数",
          "客群类别",
          "实际地址",
          "纬度（WGS84）",
          "经度（WGS84）",
          "定位精度（米）",
          "定位采集时间",
          "时区"
        });
    for (CounterStore.Entry e : entries)
      detail.add(
          new String[] {
            "" + e.id,
            "" + e.sessionId,
            date(e.time),
            "1",
            e.kind,
            e.address,
            e.lat == null ? "" : "" + e.lat,
            e.lon == null ? "" : "" + e.lon,
            e.accuracy == null ? "" : "" + e.accuracy,
            e.lat == null ? "" : date(e.locationTime),
            TimeZone.getDefault().getID()
          });
    summary.add(
        new String[] {
          "场次ID", "开始时间", "结束时间", "有效计时", "有效计时（秒）", "场次总人数", "筛选内人数", "筛选内已购买", "筛选内未购买", "筛选内通用",
          "筛选起点", "筛选终点"
        });
    for (CounterStore.Session s : sessions) {
      int bought = 0, no = 0, generic = 0;
      for (CounterStore.Entry e : entries)
        if (e.sessionId == s.id) {
          if (e.kind.equals("已购买")) bought++;
          else if (e.kind.equals("未购买")) no++;
          else generic++;
        }
      summary.add(
          new String[] {
            "" + s.id,
            date(s.start),
            s.end == 0 ? "未结束" : date(s.end),
            clock(s.elapsed),
            "" + (s.elapsed / 1000),
            "" + s.total,
            "" + (bought + no + generic),
            "" + bought,
            "" + no,
            "" + generic,
            date(from),
            date(to)
          });
    }
    try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(file))) {
      zip(
          z,
          "[Content_Types].xml",
          "<?xml version=\"1.0\"?><Types"
              + " xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default"
              + " Extension=\"rels\""
              + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default"
              + " Extension=\"xml\" ContentType=\"application/xml\"/><Override"
              + " PartName=\"/xl/workbook.xml\""
              + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override"
              + " PartName=\"/xl/worksheets/sheet1.xml\""
              + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override"
              + " PartName=\"/xl/worksheets/sheet2.xml\""
              + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
      zip(
          z,
          "_rels/.rels",
          "<?xml version=\"1.0\"?><Relationships"
              + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
              + " Id=\"rId1\""
              + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\""
              + " Target=\"xl/workbook.xml\"/></Relationships>");
      zip(
          z,
          "xl/workbook.xml",
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook"
              + " xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
              + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet"
              + " name=\"记录明细\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"场次汇总\" sheetId=\"2\""
              + " r:id=\"rId2\"/></sheets></workbook>");
      zip(
          z,
          "xl/_rels/workbook.xml.rels",
          "<?xml version=\"1.0\"?><Relationships"
              + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
              + " Id=\"rId1\""
              + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
              + " Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\""
              + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
              + " Target=\"worksheets/sheet2.xml\"/></Relationships>");
      zip(z, "xl/worksheets/sheet1.xml", sheet(detail));
      zip(z, "xl/worksheets/sheet2.xml", sheet(summary));
    }
  }
}
