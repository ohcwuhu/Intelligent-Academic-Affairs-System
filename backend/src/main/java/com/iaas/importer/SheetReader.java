package com.iaas.importer;

import com.iaas.common.BizException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 表格读取：把上传的 .xlsx/.xls/.csv 读成"表头 + 数据行"。
 *
 * <p>为什么必须支持 Excel：教务给过来的原始文件就是 Excel，
 * 让人先另存为 CSV 再把麻烦推回给用户，导入功能就是半个废品。
 * 反过来，导出仍然用 CSV——导出不需要格式，能打开就行。
 *
 * <p>CSV 的编码要猜：Windows 上 Excel 另存的 CSV 默认是 GBK，
 * 直接按 UTF-8 读会得到乱码，而乱码的表头会让整份文件校验失败。
 * 所以先按 UTF-8 严格解码，失败再退回 GBK。
 */
@Component
public class SheetReader {

    public record Table(List<String> header, List<List<String>> rows) {
    }

    public Table read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要导入的文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return readExcel(file);
            }
            String text = decode(file.getBytes());
            return readCsv(text);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("文件读取失败：" + e.getMessage());
        }
    }

    private Table readExcel(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            org.apache.poi.ss.usermodel.Sheet poiSheet = wb.getSheetAt(0);
            if (poiSheet == null) {
                throw new BizException("Excel 里没有工作表");
            }
            DataFormatter fmt = new DataFormatter();
            List<List<String>> all = new ArrayList<>();
            for (Row row : poiSheet) {
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    cells.add(cell == null ? "" : fmt.formatCellValue(cell).trim());
                }
                // 整行空白直接跳过：Excel 里末尾常有这种行
                if (cells.stream().anyMatch(c -> !c.isBlank())) {
                    all.add(cells);
                }
            }
            if (all.isEmpty()) {
                throw new BizException("表格里没有内容");
            }
            List<String> header = all.get(0);
            return new Table(trim(header), all.subList(1, all.size()));
        }
    }

    private Table readCsv(String text) {
        List<List<String>> all = parseCsv(text);
        if (all.isEmpty()) {
            throw new BizException("CSV 里没有内容");
        }
        return new Table(trim(all.get(0)), all.subList(1, all.size()));
    }

    /** 去 BOM、去空列，顺手把表头里的全角空格清掉。 */
    private List<String> trim(List<String> header) {
        List<String> out = new ArrayList<>();
        for (String h : header) {
            out.add(h.replace("\uFEFF", "").replace("\u3000", " ").trim());
        }
        return out;
    }

    /** 先 UTF-8 严格解码，失败退回 GBK。 */
    private String decode(byte[] bytes) {
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (Exception ignore) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    /**
     * CSV 解析：支持引号包裹、引号内的逗号与换行、双写引号转义。
     * 不用第三方库，因为规则就这么几条，写出来比引依赖更好审。
     */
    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> inQuotes = true;
                case ',' -> {
                    row.add(cell.toString().trim());
                    cell.setLength(0);
                }
                case '\r' -> {
                    // 交给 \n 处理，避免多插一行
                }
                case '\n' -> {
                    row.add(cell.toString().trim());
                    cell.setLength(0);
                    if (row.stream().anyMatch(s -> !s.isBlank())) {
                        rows.add(row);
                    }
                    row = new ArrayList<>();
                }
                default -> cell.append(c);
            }
        }
        row.add(cell.toString().trim());
        if (row.stream().anyMatch(s -> !s.isBlank())) {
            rows.add(row);
        }
        return rows;
    }
}
