package com.iaas.importer;

import com.iaas.common.R;
import com.iaas.common.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 批量导入接口。
 *
 * <p>两步式：先 preview 校验，再 commit 入库。前端先调 preview 把问题列出来，
 * 教务改完文件再 commit。commit 也走同一套校验，不会因为绕过前端就先写进去。
 */
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService service;

    /** 可导入的类型、模板表头与示例行。 */
    @GetMapping("/targets")
    public R<List<ImportDtos.Target>> targets() {
        requireStaff();
        return R.ok(service.targets());
    }

    /** 系统里已有的学院/专业/班级代码，用于填表时对照。 */
    @GetMapping("/dictionary")
    public R<Map<String, Object>> dictionary() {
        requireStaff();
        return R.ok(service.dictionary());
    }

    /** 只校验不入库。 */
    @PostMapping("/preview")
    public R<ImportDtos.Report> preview(@RequestParam String type,
                                        @RequestParam("file") MultipartFile file) {
        return R.ok(service.preview(type, file));
    }

    /** 校验通过后入库。 */
    @PostMapping("/commit")
    public R<ImportDtos.Report> commit(@RequestParam String type,
                                       @RequestParam("file") MultipartFile file) {
        return R.ok(service.commit(type, file));
    }

    private void requireStaff() {
        UserContext.require();
        if (!UserContext.require().isStaff()) {
            throw com.iaas.common.BizException.forbidden("仅教务人员可导入数据");
        }
    }
}
