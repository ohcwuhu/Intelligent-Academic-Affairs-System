package com.iaas.exam;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.UserContext;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.EnrollmentDtos;
import com.iaas.enrollment.EnrollmentService;
import com.iaas.exam.entity.Exam;
import com.iaas.exam.mapper.ExamMapper;
import com.iaas.system.entity.Term;
import com.iaas.system.mapper.TermMapper;
import com.iaas.teaching.entity.TeachingClass;
import com.iaas.teaching.mapper.TeachingClassMapper;
import com.iaas.teacher.entity.Teacher;
import com.iaas.teacher.mapper.TeacherMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考试安排。
 *
 * <p>两条业务规则：
 * <ol>
 *   <li><b>学生只看到自己选了课的考试</b>：考试挂在教学班上，按本人选课过滤；
 *       退课后考试自然消失，不需要额外同步。</li>
 *   <li><b>冲突提示不阻断保存</b>：同一教室同一时段被两场考试占用是真实会发生的
 *       （借教室、分考场），系统只负责把冲突摆出来，由教务决定怎么调。</li>
 * </ol>
 *
 * <p>取数一律先批量查再在内存里拼（课程、教师、教学班各一次查询），
 * 不按行回查——这是列表接口最容易踩的性能坑。
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ExamMapper mapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final TermMapper termMapper;
    private final EnrollmentService enrollmentService;

    // ------------------------------------------------------------------
    // 学生侧
    // ------------------------------------------------------------------

    /** 我的考试：按本人本学期选课过滤，按日期与开考时间排序。 */
    public List<ExamDtos.Row> my(Long termId) {
        Long studentId = UserContext.require().requireStudentId();
        Long term = termId != null ? termId : enrollmentService.currentTermId();
        List<Long> classIds = enrollmentService.myCourses(studentId, term).stream()
                .map(EnrollmentDtos.MyCourse::teachingClassId)
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        List<Exam> exams = mapper.selectList(Wrappers.<Exam>lambdaQuery()
                .in(Exam::getTeachingClassId, classIds)
                .orderByAsc(Exam::getExamDate)
                .orderByAsc(Exam::getStartTime));
        List<ExamDtos.Row> rows = toRows(exams);
        return markConflicts(rows);
    }

    // ------------------------------------------------------------------
    // 教务与教师侧
    // ------------------------------------------------------------------

    /**
     * 考试列表。教师只能看自己任教教学班的考试，
     * 与"教学班名单只有本人能看"是同一条边界。
     */
    public List<ExamDtos.Row> list(Long termId, Long teachingClassId) {
        UserContext.Principal me = UserContext.require();
        Long term = termId != null ? termId : enrollmentService.currentTermId();
        var query = Wrappers.<Exam>lambdaQuery()
                .eq(term != null, Exam::getTermId, term)
                .eq(teachingClassId != null, Exam::getTeachingClassId, teachingClassId)
                .orderByAsc(Exam::getExamDate)
                .orderByAsc(Exam::getStartTime);

        if (me.isTeacher()) {
            List<Long> mine = teachingClassMapper.selectList(Wrappers.<TeachingClass>lambdaQuery()
                            .eq(TeachingClass::getTeacherId, me.requireTeacherId()))
                    .stream().map(TeachingClass::getId).toList();
            if (mine.isEmpty()) {
                return List.of();
            }
            query.in(Exam::getTeachingClassId, mine);
        } else if (!me.isStaff()) {
            throw BizException.forbidden("学生请使用「我的考试」查询");
        }
        return toRows(mapper.selectList(query));
    }

    /** 教务安排考试。返回本次发现的冲突，不阻断保存。 */
    @Transactional(rollbackFor = Exception.class)
    public ExamDtos.SaveResult save(ExamDtos.SaveRequest req) {
        if (req.teachingClassId() == null) {
            throw new BizException("请选择教学班");
        }
        TeachingClass tc = teachingClassMapper.selectById(req.teachingClassId());
        if (tc == null) {
            throw BizException.notFound("教学班");
        }
        LocalDate date = parseDate(req.examDate());
        LocalTime start = parseTime(req.startTime(), "开始时间");
        LocalTime end = parseTime(req.endTime(), "结束时间");
        if (!end.isAfter(start)) {
            throw new BizException("结束时间必须晚于开始时间");
        }

        Exam entity = new Exam();
        entity.setId(req.id());
        entity.setTeachingClassId(tc.getId());
        entity.setTermId(tc.getTermId());
        entity.setExamType(req.examType() == null || req.examType().isBlank() ? "期末考试" : req.examType());
        entity.setExamDate(date);
        entity.setStartTime(start);
        entity.setEndTime(end);
        entity.setClassroom(req.classroom() == null ? null : req.classroom().strip());
        entity.setSeatNo(req.seatNo());
        entity.setNote(req.note());

        List<ExamDtos.Conflict> conflicts = conflicts(entity);

        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            if (mapper.selectById(entity.getId()) == null) {
                throw BizException.notFound("考试安排");
            }
            mapper.updateById(entity);
        }
        return new ExamDtos.SaveResult(entity.getId(), conflicts);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.selectById(id) == null) {
            throw BizException.notFound("考试安排");
        }
        mapper.deleteById(id);
    }

    // ------------------------------------------------------------------

    /**
     * 冲突检测：同一教室同一时段被占用、同一教学班同日时间重叠。
     *
     * <p>只取同学期同一天的考试在内存里比，比写一段窗口函数 SQL 更好读，
     * 而且考试这个量级（一学期几百场）完全跑得动。
     */
    private List<ExamDtos.Conflict> conflicts(Exam target) {
        List<Exam> sameDay = mapper.selectList(Wrappers.<Exam>lambdaQuery()
                .eq(Exam::getTermId, target.getTermId())
                .eq(Exam::getExamDate, target.getExamDate())
                .ne(target.getId() != null, Exam::getId, target.getId()));
        if (sameDay.isEmpty()) {
            return List.of();
        }
        Map<Long, TeachingClass> classes = teachingClassMapper.selectBatchIds(
                        sameDay.stream().map(Exam::getTeachingClassId).distinct().toList()).stream()
                .collect(Collectors.toMap(TeachingClass::getId, Function.identity(), (x, y) -> x));
        Map<Long, Course> courses = courseMapper.selectBatchIds(
                        classes.values().stream().map(TeachingClass::getCourseId).distinct().toList()).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (x, y) -> x));

        List<ExamDtos.Conflict> out = new ArrayList<>();
        for (Exam other : sameDay) {
            boolean overlap = other.getStartTime().isBefore(target.getEndTime())
                    && target.getStartTime().isBefore(other.getEndTime());
            if (!overlap) {
                continue;
            }
            TeachingClass tc = classes.get(other.getTeachingClassId());
            String label = label(courses, tc);
            if (Objects.equals(other.getTeachingClassId(), target.getTeachingClassId())) {
                out.add(new ExamDtos.Conflict("CLASS", "这个教学班当天已有考试：" + label));
            } else if (target.getClassroom() != null && !target.getClassroom().isBlank()
                    && target.getClassroom().equals(other.getClassroom())) {
                out.add(new ExamDtos.Conflict("CLASSROOM",
                        "教室 " + target.getClassroom() + " 该时段已被占用：" + label));
            }
        }
        return out;
    }

    private String label(Map<Long, Course> courses, TeachingClass tc) {
        if (tc == null) {
            return "未知教学班";
        }
        Course c = courses.get(tc.getCourseId());
        return (c == null ? "未知课程" : c.getName()) + "（" + tc.getCode() + "）";
    }

    /** 把考试记录拼成展示行。四张表各批量查一次，不按行回查。 */
    private List<ExamDtos.Row> toRows(List<Exam> exams) {
        if (exams.isEmpty()) {
            return List.of();
        }
        Map<Long, TeachingClass> classes = teachingClassMapper.selectBatchIds(
                        exams.stream().map(Exam::getTeachingClassId).distinct().toList()).stream()
                .collect(Collectors.toMap(TeachingClass::getId, Function.identity(), (x, y) -> x));
        Map<Long, Course> courses = courseMapper.selectBatchIds(
                        classes.values().stream().map(TeachingClass::getCourseId).distinct().toList()).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (x, y) -> x));
        Map<Long, Teacher> teachers = teacherMapper.selectBatchIds(
                        classes.values().stream().map(TeachingClass::getTeacherId).distinct().toList()).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (x, y) -> x));
        Map<Long, Term> terms = termMapper.selectBatchIds(
                        exams.stream().map(Exam::getTermId).distinct().toList()).stream()
                .collect(Collectors.toMap(Term::getId, Function.identity(), (x, y) -> x));

        LocalDate today = LocalDate.now();
        return exams.stream()
                .sorted(Comparator.comparing(Exam::getExamDate).thenComparing(Exam::getStartTime))
                .map(e -> {
                    TeachingClass tc = classes.get(e.getTeachingClassId());
                    Course c = tc == null ? null : courses.get(tc.getCourseId());
                    Teacher t = tc == null ? null : teachers.get(tc.getTeacherId());
                    Term term = terms.get(e.getTermId());
                    return new ExamDtos.Row(
                            e.getId(), e.getTeachingClassId(), tc == null ? null : tc.getCode(),
                            c == null ? null : c.getCode(), c == null ? null : c.getName(),
                            t == null ? null : t.getName(),
                            e.getTermId(), term == null ? null : term.getName(),
                            e.getExamType(), e.getExamDate().toString(),
                            e.getStartTime().format(TIME), e.getEndTime().format(TIME),
                            e.getExamDate() + " " + e.getStartTime().format(TIME)
                                    + "-" + e.getEndTime().format(TIME),
                            e.getClassroom(), e.getSeatNo(), e.getNote(),
                            ChronoUnit.DAYS.between(today, e.getExamDate()),
                            List.of());
                })
                .toList();
    }

    /** 给学生的考试行标出相互冲突的那几场（同一天时间重叠）。 */
    private List<ExamDtos.Row> markConflicts(List<ExamDtos.Row> rows) {
        if (rows.size() < 2) {
            return rows;
        }
        List<ExamDtos.Row> out = new ArrayList<>(rows.size());
        for (ExamDtos.Row row : rows) {
            List<String> clash = new ArrayList<>();
            for (ExamDtos.Row other : rows) {
                if (Objects.equals(row.id(), other.id()) || !row.examDate().equals(other.examDate())) {
                    continue;
                }
                if (other.startTime().compareTo(row.endTime()) < 0
                        && row.startTime().compareTo(other.endTime()) < 0) {
                    clash.add(other.courseName() + "（" + other.startTime() + "-" + other.endTime() + "）");
                }
            }
            out.add(clash.isEmpty() ? row : new ExamDtos.Row(
                    row.id(), row.teachingClassId(), row.teachingClassCode(),
                    row.courseCode(), row.courseName(), row.teacherName(),
                    row.termId(), row.termName(), row.examType(), row.examDate(),
                    row.startTime(), row.endTime(), row.timeText(),
                    row.classroom(), row.seatNo(), row.note(), row.daysAhead(), clash));
        }
        return out;
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new BizException("考试日期格式应为 yyyy-MM-dd");
        }
    }

    private static LocalTime parseTime(String s, String what) {
        try {
            return LocalTime.parse(s.length() == 5 ? s : s + ":00");
        } catch (Exception e) {
            throw new BizException(what + "格式应为 HH:mm");
        }
    }
}
