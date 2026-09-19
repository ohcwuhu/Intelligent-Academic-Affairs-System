package com.iaas.course;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iaas.common.BizException;
import com.iaas.common.PageResult;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseMapper courseMapper;
    /** 课程库分页查询。全体登录用户可读，便于学生查课程信息。 */
    @GetMapping
    public R<PageResult<Course>> page(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String courseType,
                                      @RequestParam(required = false) Long collegeId) {
        UserContext.require();
        var query = Wrappers.<Course>lambdaQuery()
                .eq(courseType != null && !courseType.isBlank(), Course::getCourseType, courseType)
                .eq(collegeId != null, Course::getCollegeId, collegeId)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(Course::getCode, keyword).or().like(Course::getName, keyword))
                .orderByAsc(Course::getCode);
        IPage<Course> result = courseMapper.selectPage(new Page<>(page, size), query);
        return R.ok(new PageResult<>(result.getTotal(), result.getCurrent(),
                result.getSize(), result.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Course> get(@PathVariable Long id) {
        UserContext.require();
        Course c = courseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("课程");
        }
        return R.ok(c);
    }

    @PostMapping
    public R<Long> save(@RequestBody Course req) {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可维护课程库");
        }
        if (req.getCode() == null || req.getCode().isBlank()
                || req.getName() == null || req.getName().isBlank()) {
            throw new BizException("课程代码与名称不能为空");
        }
        if (req.getCredit() == null || req.getCredit().signum() <= 0) {
            throw new BizException("学分必须大于 0");
        }
        if (req.getCollegeId() == null) {
            throw new BizException("请选择开课学院");
        }
        if (req.getId() == null) {
            Long dup = courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                    .eq(Course::getCode, req.getCode()));
            if (dup != null && dup > 0) {
                throw new BizException("课程代码已存在：" + req.getCode());
            }
            if (req.getHours() == null) {
                req.setHours(req.getCredit().intValue() * 16);
            }
            if (req.getCourseType() == null) {
                req.setCourseType("选修");
            }
            if (req.getAssessType() == null) {
                req.setAssessType("考试");
            }
            req.setStatus(1);
            courseMapper.insert(req);
        } else {
            if (courseMapper.selectById(req.getId()) == null) {
                throw BizException.notFound("课程");
            }
            courseMapper.updateById(req);
        }
        return R.ok(req.getId());
    }
}
