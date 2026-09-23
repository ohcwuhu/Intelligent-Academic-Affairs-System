package com.iaas.workbench;

import com.iaas.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/** 教务工作台：一屏交代"今天要处理什么"。 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkbenchService service;

    @GetMapping
    public R<WorkbenchService.Workbench> load() {
        return R.ok(service.load());
    }
}
