package com.iaas.program;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.enrollment.entity.Enrollment;
import com.iaas.enrollment.mapper.EnrollmentMapper;
import com.iaas.program.entity.Program;
import com.iaas.program.entity.ProgramCourse;
import com.iaas.program.mapper.ProgramCourseMapper;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import com.iaas.teaching.entity.TeachingClass;
import com.iaas.teaching.mapper.TeachingClassMapper;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 选课时的计划提示。
 *
 * <p>学生选课时最需要知道两件事：这门课算不算我的毕业学分（在不在培养计划里、
 * 属于哪个模块），以及我以前修过没有（已通过=刷分，未通过=重修，都涉及收费与成绩口径）。
 * 这两件事系统里都有数据，之前只是没在选课那一刻端出来。
 *
 * <p>对齐方式按课程 ID，不按课程名：培养方案导入时已经把计划课程对到课程库并记下
 * `course_id`，而选课列表给的也是课程 ID——用 ID 对不会出现同名课程串味。
 */
@Service
@RequiredArgsConstructor
public class PlanHintService {

    private final StudentMapper studentMapper;
    private final ProgramService programService;
    private final ProgramCourseMapper programCourseMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final TeachingClassMapper teachingClassMapper;

    /** 一条提示：计划归属 + 既往修读情况。 */
    public record Hint(
            Long courseId, boolean inPlan, String module, Integer planTerm,
            String required, BigDecimal passedScore, BigDecimal failedScore, boolean inCurrentTerm) {
    }

    public List<Hint> hints(Long studentId, Long termId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            return List.of();
        }
        Program program = programService.currentForMajor(student.getMajorId());

        Map<Long, ProgramCourse> byCourse = new LinkedHashMap<>();
        if (program != null) {
            for (ProgramCourse pc : programCourseMapper.selectList(
                    Wrappers.<ProgramCourse>lambdaQuery().eq(ProgramCourse::getProgramId, program.getId()))) {
                if (pc.getCourseId() != null) {
                    // 同一门课程库课程若被多条计划课程指向，优先保留理论课：
                    // 实践环节与理论课本就是两回事，显示错了会误导选课
                    ProgramCourse exists = byCourse.get(pc.getCourseId());
                    if (exists == null || ("实践".equals(exists.getCourseType())
                            && !"实践".equals(pc.getCourseType()))) {
                        byCourse.put(pc.getCourseId(), pc);
                    }
                }
            }
        }

        List<Enrollment> mine = enrollmentMapper.selectList(Wrappers.<Enrollment>lambdaQuery()
                .eq(Enrollment::getStudentId, studentId)
                .eq(Enrollment::getStatus, "SELECTED"));
        Map<Long, TeachingClass> classes = mine.isEmpty() ? Map.of()
                : teachingClassMapper.selectBatchIds(
                        mine.stream().map(Enrollment::getTeachingClassId).distinct().toList()).stream()
                .collect(Collectors.toMap(TeachingClass::getId, t -> t, (a, b) -> a));

        Map<Long, BigDecimal> passed = new LinkedHashMap<>();
        Map<Long, BigDecimal> failed = new LinkedHashMap<>();
        java.util.Set<Long> currentTerm = new java.util.HashSet<>();
        for (Enrollment e : mine) {
            TeachingClass tc = classes.get(e.getTeachingClassId());
            if (tc == null) {
                continue;
            }
            Long courseId = tc.getCourseId();
            if (Objects.equals(e.getTermId(), termId)) {
                currentTerm.add(courseId);
            }
            BigDecimal score = e.getScore();
            if (score == null) {
                continue;
            }
            if (score.compareTo(new BigDecimal("60")) >= 0) {
                passed.merge(courseId, score, BigDecimal::max); // 记最高一次
            } else {
                failed.merge(courseId, score, BigDecimal::min);
            }
        }

        // 计划内 + 有修读记录的课都要给提示：计划内的用于判断"算不算毕业学分"，
        // 有记录的用于判断"会不会是重修/刷分"
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        ids.addAll(byCourse.keySet());
        ids.addAll(passed.keySet());
        ids.addAll(failed.keySet());

        List<Hint> out = new ArrayList<>();
        for (Long courseId : ids) {
            ProgramCourse pc = byCourse.get(courseId);
            out.add(new Hint(courseId, pc != null,
                    pc == null ? null : pc.getModule(),
                    pc == null ? null : pc.getTermNo(),
                    pc == null ? null : pc.getRequired(),
                    passed.get(courseId), failed.get(courseId),
                    currentTerm.contains(courseId)));
        }
        return out;
    }
}
