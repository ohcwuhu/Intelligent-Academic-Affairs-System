package com.iaas.teacher;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iaas.common.BizException;
import com.iaas.common.PageResult;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import com.iaas.teacher.entity.Teacher;
import com.iaas.teacher.mapper.TeacherMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    private final TeacherMapper teacherMapper;

    public TeacherController(TeacherMapper teacherMapper) {
        this.teacherMapper = teacherMapper;
    }

    @GetMapping
    public R<PageResult<Teacher>> page(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Long collegeId) {
        UserContext.Principal me = UserContext.require();
        if (!me.isStaff() && !me.isTeacher()) {
            throw BizException.forbidden("无权查看教师列表");
        }
        var query = Wrappers.<Teacher>lambdaQuery()
                .eq(collegeId != null, Teacher::getCollegeId, collegeId)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(Teacher::getTeacherNo, keyword).or().like(Teacher::getName, keyword))
                .orderByAsc(Teacher::getTeacherNo);
        IPage<Teacher> result = teacherMapper.selectPage(new Page<>(page, size), query);
        return R.ok(new PageResult<>(result.getTotal(), result.getCurrent(),
                result.getSize(), result.getRecords()));
    }

    /** 教师档案详情。教师只能查本人。 */
    @GetMapping("/{id}")
    public R<Teacher> get(@PathVariable Long id) {
        UserContext.Principal me = UserContext.require();
        if (!me.isStaff() && !me.isTeacher()) {
            throw BizException.forbidden("无权查看教师档案");
        }
        if (me.isTeacher() && !me.refId().equals(id)) {
            throw BizException.forbidden("只能查看本人档案");
        }
        Teacher t = teacherMapper.selectById(id);
        if (t == null) {
            throw BizException.notFound("教师");
        }
        return R.ok(t);
    }

    @PostMapping
    public R<Long> save(@RequestBody Teacher req) {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可维护教师档案");
        }
        if (req.getTeacherNo() == null || req.getTeacherNo().isBlank()
                || req.getName() == null || req.getName().isBlank()) {
            throw new BizException("工号与姓名不能为空");
        }
        if (req.getCollegeId() == null) {
            throw new BizException("请选择所属学院");
        }
        if (req.getId() == null) {
            Long dup = teacherMapper.selectCount(Wrappers.<Teacher>lambdaQuery()
                    .eq(Teacher::getTeacherNo, req.getTeacherNo()));
            if (dup != null && dup > 0) {
                throw new BizException("工号已存在：" + req.getTeacherNo());
            }
            if (req.getStatus() == null) {
                req.setStatus("在职");
            }
            if (req.getGender() == null) {
                req.setGender("男");
            }
            teacherMapper.insert(req);
        } else {
            if (teacherMapper.selectById(req.getId()) == null) {
                throw BizException.notFound("教师");
            }
            teacherMapper.updateById(req);
        }
        return R.ok(req.getId());
    }
}
