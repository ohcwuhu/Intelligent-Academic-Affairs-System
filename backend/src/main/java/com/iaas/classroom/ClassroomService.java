package com.iaas.classroom;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.EnrollmentService;
import com.iaas.system.entity.Term;
import com.iaas.system.mapper.TermMapper;
import com.iaas.teaching.entity.TeachingClass;
import com.iaas.teaching.mapper.TeachingClassMapper;
import com.iaas.teacher.entity.Teacher;
import com.iaas.teacher.mapper.TeacherMapper;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 教室使用情况。
 *
 * <p>数据来源就是排课结果（teaching_class）——教室有没有被占，取决于有没有课排在它。
 * 不另建"教室占用表"：那样一旦排课改了、占用表没同步，看板就会骗人。
 *
 * <p>教室清单也来自排课数据里出现过的教室名，不维护单独的教室档案：
 * 演示环境里这样最省事；真实部署应当有一张教室基础表（含容量、类型、是否可用），
 * 这一点写在 PRD 的已知限制里。
 */
@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final TeachingClassMapper teachingClassMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final TermMapper termMapper;
    private final EnrollmentService enrollmentService;

    /** 某学期全部占用，前端按星期/节次自行筛，减少来回请求。 */
    public List<ClassroomDtos.Occupancy> all(Long termId) {
        Long term = termId != null ? termId : enrollmentService.currentTermId();
        List<TeachingClass> classes = teachingClassMapper.selectList(
                Wrappers.<TeachingClass>lambdaQuery()
                        .eq(TeachingClass::getTermId, term)
                        .isNotNull(TeachingClass::getClassroom)
                        .ne(TeachingClass::getClassroom, "")
                        .ne(TeachingClass::getStatus, "停开")
                        .orderByAsc(TeachingClass::getClassroom)
                        .orderByAsc(TeachingClass::getWeekday)
                        .orderByAsc(TeachingClass::getStartSection));
        if (classes.isEmpty()) {
            return List.of();
        }
        Map<Long, Course> courses = courseMapper.selectBatchIds(
                        classes.stream().map(TeachingClass::getCourseId).distinct().toList()).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (x, y) -> x));
        Map<Long, Teacher> teachers = teacherMapper.selectBatchIds(
                        classes.stream().map(TeachingClass::getTeacherId).distinct().toList()).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (x, y) -> x));
        return classes.stream().map(tc -> toOccupancy(tc, courses, teachers)).toList();
    }

    /** 某个时段的全景：占用的教室与被占原因，以及这段时间空着的教室。 */
    public ClassroomDtos.Slot slot(Long termId, Integer weekday, Integer startSection, Integer endSection) {
        if (weekday == null || startSection == null || endSection == null) {
            throw new BizException("请选择星期与节次");
        }
        if (startSection > endSection) {
            throw new BizException("起始节次不能大于结束节次");
        }
        Long term = termId != null ? termId : enrollmentService.currentTermId();
        Term termEntity = term == null ? null : termMapper.selectById(term);
        List<ClassroomDtos.Occupancy> all = all(term);
        List<ClassroomDtos.Occupancy> busy = all.stream()
                .filter(o -> o.weekday().equals(weekday)
                        && o.startSection() <= endSection && startSection <= o.endSection())
                .toList();
        // 教室清单 = 本学期排过课的教室 + 历史学期出现过的教室（换学期也能查）
        List<String> rooms = teachingClassMapper.selectList(
                        Wrappers.<TeachingClass>lambdaQuery()
                                .isNotNull(TeachingClass::getClassroom)
                                .ne(TeachingClass::getClassroom, ""))
                .stream().map(TeachingClass::getClassroom).distinct().sorted().toList();
        List<String> busyRooms = busy.stream().map(ClassroomDtos.Occupancy::classroom).distinct().toList();
        List<String> free = new ArrayList<>(rooms);
        free.removeAll(busyRooms);
        return new ClassroomDtos.Slot(term, termEntity == null ? null : termEntity.getName(),
                weekday, weekdayText(weekday), startSection, endSection, busy, free);
    }

    private ClassroomDtos.Occupancy toOccupancy(TeachingClass tc, Map<Long, Course> courses,
                                                Map<Long, Teacher> teachers) {
        Course c = courses.get(tc.getCourseId());
        Teacher t = teachers.get(tc.getTeacherId());
        return new ClassroomDtos.Occupancy(
                tc.getClassroom(), tc.getWeekday(), weekdayText(tc.getWeekday()),
                tc.getStartSection(), tc.getEndSection(),
                tc.getStartSection() + "-" + tc.getEndSection() + "节",
                c == null ? null : c.getName(), c == null ? null : c.getCode(),
                tc.getCode(), t == null ? null : t.getName(),
                tc.getStartWeek(), tc.getEndWeek(), tc.getWeekType());
    }

    private static String weekdayText(Integer weekday) {
        String[] names = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return weekday != null && weekday >= 1 && weekday <= 7 ? names[weekday] : "待定";
    }
}
