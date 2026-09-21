package com.iaas.grade;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.UserContext;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.EnrollmentDtos;
import com.iaas.enrollment.EnrollmentService;
import com.iaas.enrollment.entity.Enrollment;
import com.iaas.enrollment.mapper.EnrollmentMapper;
import com.iaas.grade.entity.GradeComponent;
import com.iaas.grade.mapper.GradeComponentMapper;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import com.iaas.teaching.entity.TeachingClass;
import com.iaas.teaching.mapper.TeachingClassMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成绩构成（平时/期中/期末）。
 *
 * <p>两条边界：
 * <ol>
 *   <li><b>教师只能录本人任教教学班的分项</b>：与录总评成绩同一条边界，
 *       否则任何教师都能改别人的学生成绩。</li>
 *   <li><b>权重合计不强制等于 100</b>：不同课程口径不一样（有的课平时占 50），
 *       系统只把"当前合计"显示出来提醒，不当成错误拦住老师。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class GradeComponentService {

    private final GradeComponentMapper mapper;
    private final EnrollmentMapper enrollmentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final EnrollmentService enrollmentService;

    /** 教师端：按教学班列出每个学生的分项。 */
    public List<GradeDtos.StudentComponents> byTeachingClass(Long teachingClassId) {
        UserContext.Principal me = UserContext.require();
        TeachingClass tc = teachingClassMapper.selectById(teachingClassId);
        if (tc == null) {
            throw BizException.notFound("教学班");
        }
        if (me.isTeacher() && !Objects.equals(tc.getTeacherId(), me.requireTeacherId())) {
            throw BizException.forbidden("只能查看本人任教教学班的分项成绩");
        }
        if (!me.isTeacher() && !me.isStaff()) {
            throw BizException.forbidden("学生请使用「成绩与学分」查看自己的构成");
        }
        List<Enrollment> list = enrollmentMapper.selectList(Wrappers.<Enrollment>lambdaQuery()
                .eq(Enrollment::getTeachingClassId, teachingClassId)
                .eq(Enrollment::getStatus, "SELECTED"));
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> students = studentMapper.selectBatchIds(
                        list.stream().map(Enrollment::getStudentId).distinct().toList()).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<GradeComponent>> byEnrollment = mapper.selectList(
                        Wrappers.<GradeComponent>lambdaQuery()
                                .in(GradeComponent::getEnrollmentId,
                                        list.stream().map(Enrollment::getId).toList()))
                .stream().collect(Collectors.groupingBy(GradeComponent::getEnrollmentId));
        return list.stream().map(e -> {
            Student s = students.get(e.getStudentId());
            return new GradeDtos.StudentComponents(e.getId(),
                    s == null ? null : s.getStudentNo(), s == null ? null : s.getName(),
                    e.getScore(),
                    (byEnrollment.getOrDefault(e.getId(), List.of())).stream()
                            .map(GradeComponentService::toDto).toList());
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public int save(List<GradeDtos.SaveComponent> items) {
        UserContext.Principal me = UserContext.require();
        int saved = 0;
        for (GradeDtos.SaveComponent req : items) {
            if (req.enrollmentId() == null || req.item() == null || req.item().isBlank()) {
                continue;
            }
            if (req.score() != null
                    && (req.score().compareTo(BigDecimal.ZERO) < 0
                    || req.score().compareTo(new BigDecimal("100")) > 0)) {
                throw new BizException("分项成绩必须在 0 到 100 之间");
            }
            Enrollment e = enrollmentMapper.selectById(req.enrollmentId());
            if (e == null || "DROPPED".equals(e.getStatus())) {
                throw BizException.notFound("选课记录");
            }
            if (me.isTeacher()) {
                TeachingClass tc = teachingClassMapper.selectById(e.getTeachingClassId());
                if (tc == null || !Objects.equals(tc.getTeacherId(), me.requireTeacherId())) {
                    throw BizException.forbidden("只能录入本人任教教学班的分项成绩");
                }
            } else if (!me.isStaff()) {
                throw BizException.forbidden("学生无权录入成绩");
            }
            GradeComponent existing = mapper.selectOne(Wrappers.<GradeComponent>lambdaQuery()
                    .eq(GradeComponent::getEnrollmentId, req.enrollmentId())
                    .eq(GradeComponent::getItem, req.item().strip()));
            if (existing == null) {
                GradeComponent c = new GradeComponent();
                c.setEnrollmentId(req.enrollmentId());
                c.setItem(req.item().strip());
                c.setWeight(req.weight());
                c.setScore(req.score());
                mapper.insert(c);
            } else {
                existing.setWeight(req.weight());
                existing.setScore(req.score());
                mapper.updateById(existing);
            }
            saved++;
        }
        return saved;
    }

    /** 学生端：按课程返回自己的分项构成（含未出成绩的课）。 */
    public List<GradeDtos.CourseComponents> mine() {
        Long studentId = UserContext.require().requireStudentId();
        List<EnrollmentDtos.MyCourse> courses = enrollmentService.myCourses(studentId, null);
        if (courses.isEmpty()) {
            return List.of();
        }
        Map<Long, List<GradeComponent>> byEnrollment = mapper.selectList(
                        Wrappers.<GradeComponent>lambdaQuery()
                                .in(GradeComponent::getEnrollmentId,
                                        courses.stream().map(EnrollmentDtos.MyCourse::enrollmentId).toList()))
                .stream().collect(Collectors.groupingBy(GradeComponent::getEnrollmentId));
        List<GradeDtos.CourseComponents> out = new ArrayList<>();
        for (EnrollmentDtos.MyCourse c : courses) {
            List<GradeComponent> items = byEnrollment.getOrDefault(c.enrollmentId(), List.of());
            if (items.isEmpty()) {
                continue; // 没录分项就不显示这一行，避免一屏空表
            }
            out.add(new GradeDtos.CourseComponents(c.courseCode(), c.courseName(), c.termName(),
                    c.score(), c.scoreStatus(),
                    items.stream().map(GradeComponentService::toDto).toList()));
        }
        return out;
    }

    private static GradeDtos.Component toDto(GradeComponent c) {
        return new GradeDtos.Component(c.getId(), c.getEnrollmentId(), c.getItem(),
                c.getWeight(), c.getScore());
    }

    /** 教务看课程名用（保留给扩展）。 */
    public Course course(Long courseId) {
        return courseMapper.selectById(courseId);
    }
}
