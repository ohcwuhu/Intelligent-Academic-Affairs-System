package com.iaas.classroom;

import com.iaas.common.R;
import com.iaas.common.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 教室使用情况。
 *
 * <p>只读接口，登录即可查：学生找自习室、教师找调课后的空教室、
 * 教务看整体占用，看的是同一份排课结果。
 */
@RestController
@RequestMapping("/api/classroom")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService service;

    @GetMapping("/usage")
    public R<List<ClassroomDtos.Occupancy>> usage(@RequestParam(required = false) Long termId) {
        UserContext.require();
        return R.ok(service.all(termId));
    }

    @GetMapping("/slot")
    public R<ClassroomDtos.Slot> slot(@RequestParam(required = false) Long termId,
                                      @RequestParam Integer weekday,
                                      @RequestParam Integer startSection,
                                      @RequestParam Integer endSection) {
        UserContext.require();
        return R.ok(service.slot(termId, weekday, startSection, endSection));
    }
}
