package com.iaas.importer;

import java.util.List;

public final class ImportDtos {

    private ImportDtos() {
    }

    /** 可导入的数据类型，以及它要求的表头与示例行（前端据此生成模板）。 */
    public record Target(String type, String label, String note,
                         List<String> columns, List<String> sample) {
    }

    /**
     * 单行结果。
     *
     * @param line   文件里的行号（含表头，所以数据行从 2 开始，方便对着 Excel 找）
     * @param key    业务键（课程代码/学号/工号），出错时也尽量给出，便于定位
     */
    public record RowResult(int line, String key, boolean ok, String message) {
    }

    /**
     * 导入报告。
     *
     * @param committed 是否真的写库（预览阶段为 false）
     * @param errors    前若干条错误，避免一次甩几千行给前端
     */
    public record Report(String type, String label, String fileName,
                         int total, int ok, int failed, boolean committed,
                         List<RowResult> rows, List<String> errors) {
    }
}
