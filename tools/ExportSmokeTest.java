package cn.shike.counter;

import java.io.File;
import java.util.*;

/** Desktop smoke check using the same production exporter; Android runtime is not invoked. */
public final class ExportSmokeTest {
  public static void main(String[] args) throws Exception {
    if (!ExcelExporter.clock(3_661_999).equals("01:01:01")) throw new AssertionError("clock");
    if (!ExcelExporter.escape("A&B<\"").equals("A&amp;B&lt;&quot;"))
      throw new AssertionError("escaping");
    CounterStore.Session s = new CounterStore.Session();
    s.id = 1;
    s.start = 1700000000000L;
    s.end = s.start + 120000;
    s.elapsed = 90000;
    s.total = 3;
    List<CounterStore.Entry> rows = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      CounterStore.Entry e = new CounterStore.Entry();
      e.id = i + 1;
      e.sessionId = 1;
      e.time = s.start + i * 1000;
      e.kind = new String[] {"通用", "已购买", "未购买"}[i];
      e.address = i == 0 ? "未获取位置" : "杭州 & <测试> =1+1";
      if (i > 0) {
        e.lat = 30.25;
        e.lon = 120.16;
        e.accuracy = 8.0;
        e.locationTime = e.time;
      }
      rows.add(e);
    }
    ExcelExporter.write(new File(args[0]), Collections.singletonList(s), rows, s.start, s.end);
    System.out.println("Clock / XML escaping / production XLSX export passed");
  }
}
