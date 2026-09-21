package com.iaas.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.system.entity.Major;
import com.iaas.system.entity.Term;
import com.iaas.system.mapper.MajorMapper;
import com.iaas.system.mapper.TermMapper;
import com.iaas.teaching.entity.TeachingClass;
import com.iaas.teaching.mapper.TeachingClassMapper;
import com.iaas.teacher.entity.Teacher;
import com.iaas.teacher.mapper.TeacherMapper;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 课表查询服务。
 *
 * <p>专业课表按教学班上的"面向专业/年级"筛，不按选课反推：
 * 反推会把外专业来选修的课算成这个专业的课，也会漏掉还没人选的开课。
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final TeachingClassMapper teachingClassMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final TermMapper termMapper;
    private final MajorMapper majorMapper;

    public ScheduleDtos.MajorTimetable majorTimetable(Long majorId, Integer grade, Long termId) {
        Term term = termId != null
                ? termMapper.selectById(termId)
                : termMapper.selectOne(Wrappers.<Term>lambdaQuery()
                        .eq(Term::getIsCurrent, 1).last("limit 1"));
        if (term == null) {
            throw BizException.notFound("学期");
        }

        List<TeachingClass> classes = teachingClassMapper.selectList(
                Wrappers.<TeachingClass>lambdaQuery()
                        .eq(TeachingClass::getTermId, term.getId())
                        .eq(majorId != null, TeachingClass::getMajorId, majorId)
                        .eq(grade != null, TeachingClass::getGrade, grade)
                        .ne(TeachingClass::getStatus, "停开")
                        .orderByAsc(TeachingClass::getWeekday)
                        .orderByAsc(TeachingClass::getStartSection));

        // 课程与教师各一次批量查询，不按行回查
        Map<Long, Course> courses = classes.isEmpty() ? Map.of()
                : courseMapper.selectBatchIds(classes.stream()
                        .map(TeachingClass::getCourseId).distinct().toList()).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (x, y) -> x));
        Map<Long, Teacher> teachers = classes.isEmpty() ? Map.of()
                : teacherMapper.selectBatchIds(classes.stream()
                        .map(TeachingClass::getTeacherId).distinct().toList()).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (x, y) -> x));

        List<ScheduleDtos.TimetableEntry> entries = classes.stream()
                .map(tc -> {
                    Course c = courses.get(tc.getCourseId());
                    Teacher t = teachers.get(tc.getTeacherId());
                    return new ScheduleDtos.TimetableEntry(
                            c == null ? null : c.getName(),
                            c == null ? null : c.getCode(),
                            t == null ? null : t.getName(),
                            tc.getCode(),
                            tc.getClassroom(),
                            tc.getWeekday(), tc.getStartSection(), tc.getEndSection(),
                            describe(tc),
                            c == null ? null : c.getCredit());
                })
                .toList();

        Major major = majorId == null ? null : majorMapper.selectById(majorId);
        return new ScheduleDtos.MajorTimetable(
                term.getId(), term.getName(),
                majorId, major == null ? null : major.getName(), grade,
                (int) classes.stream().map(TeachingClass::getCourseId).filter(Objects::nonNull)
                        .distinct().count(),
                entries);
    }

    /** 时间文案：与课表其它地方保持一致的说法。 */
    private static String describe(TeachingClass tc) {
        StringBuilder sb = new StringBuilder();
        sb.append(weekdayText(tc.getWeekday())).append(' ')
                .append(tc.getStartSection()).append('-').append(tc.getEndSection()).append("节");
        if (tc.getStartWeek() != null && tc.getEndWeek() != null) {
            sb.append(' ').append(tc.getStartWeek()).append('-').append(tc.getEndWeek()).append('周');
        }
        if ("ODD".equals(tc.getWeekType())) {
            sb.append("(单周)");
        } else if ("EVEN".equals(tc.getWeekType())) {
            sb.append("(双周)");
        }
        return sb.toString();
    }

    private static String weekdayText(Integer weekday) {
        if (weekday == null) {
            return "待定";
        }
        String[] names = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return weekday >= 1 && weekday <= 7 ? names[weekday] : "待定";
    }
}
