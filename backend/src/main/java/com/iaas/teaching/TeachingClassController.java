package com.iaas.teaching;

import com.iaas.common.BizException;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import com.iaas.enrollment.EnrollmentDtos;
import com.iaas.enrollment.EnrollmentService;
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

@RestController
@RequestMapping("/api/teaching-class")
@RequiredArgsConstructor
public class TeachingClassController {

    private final TeachingClassService teachingClassService;
    private final EnrollmentService enrollmentService;
    /** 教学班列表。教务与管理员可查全部；教师只能查自己的。 */
    @GetMapping
    public R<List<TeachingClassDtos.TeachingClassVO>> list(
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Boolean onlyOpen) {
        UserContext.Principal me = UserContext.require();
        Long effectiveTeacherId = teacherId;
        if (me.isTeacher()) {
            effectiveTeacherId = me.requireTeacherId();
        } else if (!me.isStaff()) {
            throw BizException.forbidden("无权查看教学班列表");
        }
        return R.ok(teachingClassService.list(termId, courseId, effectiveTeacherId,
                Boolean.TRUE.equals(onlyOpen)));
    }

    /** 学生可选课程列表。 */
    @GetMapping("/selectable")
    public R<List<TeachingClassDtos.TeachingClassVO>> selectable(
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) String keyword) {
        UserContext.require();
        Long term = termId != null ? termId : enrollmentService.currentTermId();
        return R.ok(teachingClassService.selectable(term, keyword));
    }

    @GetMapping("/{id}")
    public R<TeachingClassDtos.TeachingClassVO> get(@PathVariable Long id) {
        UserContext.require();
        return R.ok(teachingClassService.get(id));
    }

    /** 教学班名单。教师仅限本人任教的教学班。 */
    @GetMapping("/{id}/roster")
    public R<List<EnrollmentDtos.RosterItem>> roster(@PathVariable Long id) {
        UserContext.Principal me = UserContext.require();
        if (me.isStudent()) {
            throw BizException.forbidden("学生无权查看教学班名单");
        }
        if (me.isTeacher()) {
            TeachingClassDtos.TeachingClassVO tc = teachingClassService.get(id);
            if (!me.requireTeacherId().equals(tc.teacherId())) {
                throw BizException.forbidden("只能查看本人任教教学班的名单");
            }
        }
        return R.ok(enrollmentService.roster(id));
    }

    /** 新增或修改教学班（仅教务侧）。返回本次发现的排课冲突。 */
    @PostMapping
    public R<TeachingClassDtos.SaveResult> save(@RequestBody TeachingClassDtos.SaveRequest req) {
        requireStaff();
        return R.ok(teachingClassService.save(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        requireStaff();
        teachingClassService.delete(id);
        return R.ok();
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可维护教学班");
        }
    }
}
