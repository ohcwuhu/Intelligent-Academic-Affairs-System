package com.iaas.export;

import java.util.List;

/**
 * CSV 拼装。
 *
 * <p>自己写而不是引库：规则就三条——字段里有逗号/引号/换行就整体加引号、
 * 引号双写、行用 CRLF。写出来比引依赖更好审，也不会为导出加一个库。
 *
 * <p>开头写 UTF-8 BOM：Excel 双击打开时不认无 BOM 的 UTF-8，中文会变乱码，
 * 这是导出功能最容易挨骂的地方。
 */
public final class Csv {

    private static final String BOM = "\uFEFF";
    private static final String CRLF = "\r\n";

    private final StringBuilder sb = new StringBuilder(BOM);

    public Csv header(String... names) {
        return row(names);
    }

    /** 用 Object 接收，null 输出空单元格而不是 "null"。 */
    public Csv row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append(CRLF);
        return this;
    }

    public Csv rows(List<Object[]> list) {
        for (Object[] cells : list) {
            row(cells);
        }
        return this;
    }

    public String build() {
        return sb.toString();
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        // 去掉换行与制表符：表格里换行会把一行拆成两行，Excel 打开就是错行
        s = s.replace("\r", " ").replace("\n", " ").replace("\t", " ");
        if (s.contains(",") || s.contains("\"") || s.contains(" ")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
