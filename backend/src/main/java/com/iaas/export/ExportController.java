package com.iaas.export;

import com.iaas.common.UserContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * CSV 导出。
 *
 * <p>文件名用 RFC 5987 的 filename* 传中文：直接写 filename 会在部分浏览器变成乱码。
 * 内容是 UTF-8（带 BOM，见 {@link Csv}），Excel 双击能直接打开。
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService service;

    @GetMapping("/{type}")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(required = false) Long id,
                                         @RequestParam(required = false) Long termId,
                                         @RequestParam(required = false) String keyword) {
        UserContext.require();
        ExportService.CsvFile file = service.export(type, id, termId, keyword);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export.csv\"; filename*=UTF-8''" + encoded)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(file.content().getBytes(StandardCharsets.UTF_8));
    }
}
