package com.iaas.student;

import com.iaas.common.BizException;
import com.iaas.common.PageResult;
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

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    /** 学生档案分页查询。学生角色不可查看他人档案。 */
    @GetMapping
    public R<PageResult<StudentDtos.StudentVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) Long clazzId,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) String status) {
        requireStaff();
        return R.ok(studentService.page(page, size, keyword, majorId, clazzId, grade, status));
    }

    /** 我的档案。 */
    @GetMapping("/me")
    public R<StudentDtos.StudentVO> me() {
        return R.ok(studentService.get(UserContext.require().requireStudentId()));
    }

    @GetMapping("/{id}")
    public R<StudentDtos.StudentVO> get(@PathVariable Long id) {
        UserContext.Principal me = UserContext.require();
        if (me.isStudent() && !me.refId().equals(id)) {
            throw BizException.forbidden("只能查看本人档案");
        }
        if (me.isTeacher()) {
            throw BizException.forbidden("教师请通过教学班名单查看学生");
        }
        return R.ok(studentService.get(id));
    }

    @PostMapping
    public R<Long> save(@RequestBody StudentDtos.SaveRequest req) {
        requireStaff();
        return R.ok(studentService.save(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        if (!"ADMIN".equals(UserContext.require().role())) {
            throw BizException.forbidden("仅系统管理员可删除学生档案");
        }
        studentService.delete(id);
        return R.ok();
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可维护学生档案");
        }
    }
}
