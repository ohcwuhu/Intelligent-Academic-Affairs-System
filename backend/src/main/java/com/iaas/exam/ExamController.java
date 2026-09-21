package com.iaas.exam;

import com.iaas.common.BizException;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 考试查询与安排。
 *
 * <p>读接口按角色收窄：学生走 /my（只看本人选课的考试），
 * 教师走列表接口但被限定在本人任教教学班，教务看全部。
 * 写接口（安排、删除）只对教务与管理员开放。
 */
@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService service;

    /** 我的考试（学生）。 */
    @GetMapping("/my")
    public R<List<ExamDtos.Row>> my(@RequestParam(required = false) Long termId) {
        UserContext.require().requireStudentId();
        return R.ok(service.my(termId));
    }

    /** 考试列表：教务看全部，教师只看本人教学班。 */
    @GetMapping
    public R<List<ExamDtos.Row>> list(@RequestParam(required = false) Long termId,
                                      @RequestParam(required = false) Long teachingClassId) {
        return R.ok(service.list(termId, teachingClassId));
    }

    @PostMapping
    public R<ExamDtos.SaveResult> save(@RequestBody ExamDtos.SaveRequest req) {
        requireStaff();
        return R.ok(service.save(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        requireStaff();
        service.delete(id);
        return R.ok();
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可安排考试");
        }
    }
}
