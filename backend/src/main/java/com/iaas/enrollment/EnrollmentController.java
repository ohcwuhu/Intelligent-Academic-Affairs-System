package com.iaas.enrollment;

import com.iaas.common.BizException;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 选课与学业查询。
 *
 * <p>学生身份一律使用令牌中的 refId，忽略前端传入的 studentId，
 * 从根上杜绝越权（PRD 原则 PR2）。
 */
@RestController
@RequestMapping("/api/enrollment")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final StudentMapper studentMapper;
    private final com.iaas.program.PlanHintService planHintService;

    /**
     * 选课提示：这门课在不在我的培养计划里、属于哪个模块、我以前修过没有。
     * 身份取自令牌，学生只能看自己的。
     */
    @GetMapping("/plan-hints")
    public R<List<com.iaas.program.PlanHintService.Hint>> planHints(
            @RequestParam(required = false) Long termId) {
        Long studentId = UserContext.require().requireStudentId();
        Long term = termId != null ? termId : enrollmentService.currentTermId();
        return R.ok(planHintService.hints(studentId, term));
    }

    @GetMapping("/my")
    public R<List<EnrollmentDtos.MyCourse>> my(@RequestParam(required = false) Long termId,
                                              @RequestParam(required = false) Long studentId) {
        return R.ok(enrollmentService.myCourses(resolveStudentId(studentId), termId));
    }

    @GetMapping("/summary")
    public R<EnrollmentDtos.CreditSummary> summary(@RequestParam(required = false) Long studentId) {
        return R.ok(enrollmentService.creditSummary(resolveStudentId(studentId)));
    }

    @GetMapping("/conflicts")
    public R<List<EnrollmentDtos.ConflictItem>> conflicts(
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) Long studentId) {
        return R.ok(enrollmentService.conflictsOf(resolveStudentId(studentId), termId));
    }

    /** 选某门课会不会与已选课程冲突（不写库）。 */
    @GetMapping("/preview/{teachingClassId}")
    public R<List<EnrollmentDtos.ConflictItem>> preview(
            @PathVariable Long teachingClassId,
            @RequestParam(required = false) Long studentId) {
        return R.ok(enrollmentService.previewConflicts(
                resolveStudentId(studentId), teachingClassId));
    }

    @PostMapping("/select/{teachingClassId}")
    public R<EnrollmentDtos.SelectResult> select(@PathVariable Long teachingClassId,
                                                 @RequestParam(required = false) Long studentId) {
        return R.ok(enrollmentService.select(resolveStudentId(studentId), teachingClassId));
    }

    @DeleteMapping("/{enrollmentId}")
    public R<Void> drop(@PathVariable Long enrollmentId,
                        @RequestParam(required = false) Long studentId) {
        enrollmentService.drop(resolveStudentId(studentId), enrollmentId);
        return R.ok();
    }

    /**
     * 解析本次操作的目标学生。学生角色强制用令牌中的 ID（忽略入参）；
     * 教务侧必须显式指定 studentId。
     */
    private Long resolveStudentId(Long requested) {
        UserContext.Principal me = UserContext.require();
        if (me.isStudent()) {
            return me.requireStudentId();
        }
        if (!me.isStaff()) {
            throw BizException.forbidden("无权查看或操作学生选课数据");
        }
        if (requested == null) {
            throw new BizException("请指定 studentId");
        }
        Student s = studentMapper.selectById(requested);
        if (s == null) {
            throw BizException.notFound("学生");
        }
        return requested;
    }
}
