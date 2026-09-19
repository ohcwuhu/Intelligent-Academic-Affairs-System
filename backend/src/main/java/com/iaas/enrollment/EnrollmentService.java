package com.iaas.enrollment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.entity.Enrollment;
import com.iaas.enrollment.mapper.EnrollmentMapper;
import com.iaas.grade.GradePointCalculator;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import com.iaas.system.entity.Clazz;
import com.iaas.system.entity.Major;
import com.iaas.system.entity.Term;
import com.iaas.system.mapper.ClazzMapper;
import com.iaas.system.mapper.MajorMapper;
import com.iaas.system.mapper.TermMapper;
import com.iaas.teacher.entity.Teacher;
import com.iaas.teacher.mapper.TeacherMapper;
import com.iaas.teaching.entity.TeachingClass;
import com.iaas.teaching.mapper.TeachingClassMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 选课与成绩的核心服务。
 *
 * <p>三条不可动摇的规则：
 * <ol>
 *   <li>容量控制用带条件的原子 UPDATE，避免并发下超选；</li>
 *   <li>时间冲突由 {@link TimeConflictChecker} 确定性判定，不交给模型；</li>
 *   <li>学分与绩点由代码计算，不存快照。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private static final String SELECTED = "SELECTED";
    private static final String DROPPED = "DROPPED";
    private static final String OPEN = "开放";

    private final EnrollmentMapper enrollmentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final TermMapper termMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final MajorMapper majorMapper;

    @Value("${iaas.enrollment.open:true}")
    private boolean enrollmentOpen;
    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 某学期的选课与成绩记录；termId 为空则查全部。 */
    public List<EnrollmentDtos.MyCourse> myCourses(Long studentId, Long termId) {
        var query = Wrappers.<Enrollment>lambdaQuery()
                .eq(Enrollment::getStudentId, studentId)
                .eq(Enrollment::getStatus, SELECTED)
                .orderByDesc(Enrollment::getTermId)
                .orderByAsc(Enrollment::getId);
        if (termId != null) {
            query.eq(Enrollment::getTermId, termId);
        }
        return toMyCourses(enrollmentMapper.selectList(query));
    }

    /**
     * 学分与绩点汇总。
     *
     * <p>已获学分只统计成绩 ≥ 60 的课程；同一门课重修多次时只计最高一次，
     * 避免重复累计——这是教务统计里最容易算错的地方。
     */
    public EnrollmentDtos.CreditSummary creditSummary(Long studentId) {
        List<Enrollment> all = enrollmentMapper.selectList(
                Wrappers.<Enrollment>lambdaQuery()
                        .eq(Enrollment::getStudentId, studentId)
                        .eq(Enrollment::getStatus, SELECTED));

        Map<Long, Course> courseMap = loadCoursesFromEnrollments(all);
        Map<Long, BigDecimal> bestScoreByCourse = new HashMap<>();
        Set<Long> seenCourseIds = new HashSet<>();

        for (Enrollment e : all) {
            TeachingClass tc = teachingClassMapper.selectById(e.getTeachingClassId());
            if (tc == null) {
                continue;
            }
            Long courseId = tc.getCourseId();
            seenCourseIds.add(courseId);
            if (e.getScore() == null) {
                continue;
            }
            BigDecimal best = bestScoreByCourse.get(courseId);
            if (best == null || e.getScore().compareTo(best) > 0) {
                bestScoreByCourse.put(courseId, e.getScore());
            }
        }

        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal inProgress = BigDecimal.ZERO;
        int passed = 0;
        int failed = 0;
        int inProgressCount = 0;

        for (Long courseId : seenCourseIds) {
            Course course = courseMap.get(courseId);
            if (course == null) {
                continue;
            }
            BigDecimal best = bestScoreByCourse.get(courseId);
            if (best == null) {
                inProgress = inProgress.add(course.getCredit());
                inProgressCount++;
            } else if (GradePointCalculator.isPassed(best)) {
                earned = earned.add(course.getCredit());
                weighted = weighted.add(
                        GradePointCalculator.toGradePoint(best).multiply(course.getCredit()));
                passed++;
            } else {
                failed++;
            }
        }

        return new EnrollmentDtos.CreditSummary(
                earned, inProgress, GradePointCalculator.gpa(weighted, earned),
                passed, failed, inProgressCount);
    }

    /** 已选课程中的时间冲突。 */
    public List<EnrollmentDtos.ConflictItem> conflictsOf(Long studentId, Long termId) {
        Long effectiveTerm = termId != null ? termId : currentTermId();
        List<Enrollment> selected = enrollmentMapper.selectList(
                Wrappers.<Enrollment>lambdaQuery()
                        .eq(Enrollment::getStudentId, studentId)
                        .eq(Enrollment::getTermId, effectiveTerm)
                        .eq(Enrollment::getStatus, SELECTED));
        if (selected.size() < 2) {
            return List.of();
        }
        Map<Long, TeachingClass> tcMap = loadTeachingClasses(
                selected.stream().map(Enrollment::getTeachingClassId).toList());
        Map<Long, Course> courseMap = loadCourses(tcMap.values().stream()
                .map(TeachingClass::getCourseId).toList());

        List<TeachingClass> list = selected.stream()
                .map(e -> tcMap.get(e.getTeachingClassId()))
                .filter(Objects::nonNull)
                .toList();
        List<EnrollmentDtos.ConflictItem> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                TeachingClass a = list.get(i);
                TeachingClass b = list.get(j);
                if (TimeConflictChecker.conflicts(a, b)) {
                    result.add(new EnrollmentDtos.ConflictItem(
                            courseName(courseMap, a), TimeConflictChecker.describe(a),
                            courseName(courseMap, b), TimeConflictChecker.describe(b)));
                }
            }
        }
        return result;
    }

    /** 选课前的冲突预检：不写库，只回答"选了会不会撞"。 */
    public List<EnrollmentDtos.ConflictItem> previewConflicts(Long studentId, Long teachingClassId) {
        TeachingClass target = teachingClassMapper.selectById(teachingClassId);
        if (target == null) {
            throw BizException.notFound("教学班");
        }
        List<Enrollment> selected = enrollmentMapper.selectList(
                Wrappers.<Enrollment>lambdaQuery()
                        .eq(Enrollment::getStudentId, studentId)
                        .eq(Enrollment::getTermId, target.getTermId())
                        .eq(Enrollment::getStatus, SELECTED));
        Map<Long, TeachingClass> tcMap = loadTeachingClasses(
                selected.stream().map(Enrollment::getTeachingClassId).toList());
        Map<Long, Course> courseMap = loadCourses(concat(target.getCourseId(),
                tcMap.values().stream().map(TeachingClass::getCourseId).toList()));

        List<EnrollmentDtos.ConflictItem> result = new ArrayList<>();
        for (TeachingClass other : tcMap.values()) {
            // 已选上这门课时不要跟自己比：那会报出"数据结构 与 数据结构 冲突"这种废话
            if (other.getId().equals(target.getId())) {
                continue;
            }
            if (TimeConflictChecker.conflicts(target, other)) {
                result.add(new EnrollmentDtos.ConflictItem(
                        courseName(courseMap, target), TimeConflictChecker.describe(target),
                        courseName(courseMap, other), TimeConflictChecker.describe(other)));
            }
        }
        return result;
    }

    /** 教学班名单，供教师与教务查看。 */
    public List<EnrollmentDtos.RosterItem> roster(Long teachingClassId) {
        List<Enrollment> list = enrollmentMapper.selectList(
                Wrappers.<Enrollment>lambdaQuery()
                        .eq(Enrollment::getTeachingClassId, teachingClassId)
                        .eq(Enrollment::getStatus, SELECTED));
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> studentMap = loadMap(studentMapper
                .selectBatchIds(list.stream().map(Enrollment::getStudentId).toList()), Student::getId);
        List<Student> students = new ArrayList<>(studentMap.values());
        Map<Long, Clazz> clazzMap = loadMap(clazzMapper.selectBatchIds(
                students.stream().map(Student::getClazzId).distinct().toList()), Clazz::getId);
        Map<Long, Major> majorMap = loadMap(majorMapper.selectBatchIds(
                students.stream().map(Student::getMajorId).distinct().toList()), Major::getId);

        List<EnrollmentDtos.RosterItem> result = new ArrayList<>();
        for (Enrollment e : list) {
            Student s = studentMap.get(e.getStudentId());
            if (s == null) {
                continue;
            }
            Clazz c = clazzMap.get(s.getClazzId());
            Major m = majorMap.get(s.getMajorId());
            result.add(new EnrollmentDtos.RosterItem(
                    e.getId(), s.getId(), s.getStudentNo(), s.getName(),
                    c == null ? null : c.getName(), m == null ? null : m.getName(),
                    e.getScore(), e.getScoreStatus(), e.getGradePoint(),
                    GradePointCalculator.isPassed(e.getScore())));
        }
        result.sort((x, y) -> x.studentNo().compareTo(y.studentNo()));
        return result;
    }

    // ------------------------------------------------------------------
    // 选课 / 退课
    // ------------------------------------------------------------------

    /**
     * 学生选课。
     *
     * <p>执行顺序固定为：选课开关 → 教学班可选性 → 重复修读校验 →
     * 容量原子占用 → 时间冲突预检 → 落库。
     * 冲突不阻断选课，而是返回冲突清单由学生自行取舍——重修免听等场景下，
     * 冲突是允许存在的，系统的职责是如实提示而不是替学生决定。
     */
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentDtos.SelectResult select(Long studentId, Long teachingClassId) {
        if (!enrollmentOpen) {
            throw new BizException("当前不在选课开放时间内");
        }
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw BizException.notFound("学生");
        }
        if ("休学".equals(student.getStatus()) || "退学".equals(student.getStatus())) {
            throw new BizException("学籍状态为「" + student.getStatus() + "」，不能选课");
        }

        TeachingClass tc = teachingClassMapper.selectById(teachingClassId);
        if (tc == null) {
            throw BizException.notFound("教学班");
        }
        if (!OPEN.equals(tc.getStatus())) {
            throw new BizException("该教学班当前状态为「" + tc.getStatus() + "」，不能选课");
        }
        if (!Objects.equals(tc.getTermId(), currentTermId())) {
            throw new BizException("只能选当前学期的课程");
        }

        List<Enrollment> existing = enrollmentMapper.selectList(
                Wrappers.<Enrollment>lambdaQuery()
                        .eq(Enrollment::getStudentId, studentId)
                        .eq(Enrollment::getTermId, tc.getTermId())
                        .eq(Enrollment::getStatus, SELECTED));

        // 重复点击同一个教学班：返回既有记录，保持幂等
        Enrollment same = existing.stream()
                .filter(e -> Objects.equals(e.getTeachingClassId(), teachingClassId))
                .findFirst().orElse(null);
        if (same != null) {
            return new EnrollmentDtos.SelectResult(same.getId(), "你已经选过这门课", List.of());
        }

        Map<Long, TeachingClass> existingTc = loadTeachingClasses(
                existing.stream().map(Enrollment::getTeachingClassId).toList());
        boolean duplicatedCourse = existingTc.values().stream()
                .anyMatch(x -> Objects.equals(x.getCourseId(), tc.getCourseId()));
        if (duplicatedCourse) {
            throw new BizException("本学期已选过该课程的其它教学班，不能重复选课");
        }

        // 时间冲突：拦在这里而不是"选上了再提示"。
        // 界面上冲突课程本来就不给选课入口，接口再放行就等于两套说法；
        // 而两门课压在同一个时段这件事，学生自己很难在选课那一刻发现。
        List<EnrollmentDtos.ConflictItem> conflicts = previewConflicts(studentId, teachingClassId);
        if (!conflicts.isEmpty()) {
            EnrollmentDtos.ConflictItem c = conflicts.get(0);
            throw new BizException(400, "与已选课程时间冲突：" + c.courseB() + "（" + c.timeB()
                    + "）与本课程（" + c.timeA() + "）重叠。"
                    + "如确需修读，请按学生手册第二十三条申请免听或间听后再办理。");
        }

        // 容量：条件更新，enrolled < capacity 才 +1，可靠地防止并发超选
        int affected = teachingClassMapper.update(null,
                Wrappers.<TeachingClass>lambdaUpdate()
                        .eq(TeachingClass::getId, teachingClassId)
                        .apply("enrolled < capacity")
                        .setSql("enrolled = enrolled + 1"));
        if (affected == 0) {
            throw new BizException("该教学班名额已满");
        }

        // 此前退过这门课时复用原记录，否则会撞唯一键
        Enrollment dropped = enrollmentMapper.selectOne(
                Wrappers.<Enrollment>lambdaQuery()
                        .eq(Enrollment::getStudentId, studentId)
                        .eq(Enrollment::getTeachingClassId, teachingClassId)
                        .eq(Enrollment::getStatus, DROPPED));

        Long enrollmentId;
        if (dropped != null) {
            dropped.setStatus(SELECTED);
            dropped.setSelectedAt(LocalDateTime.now());
            enrollmentMapper.updateById(dropped);
            enrollmentId = dropped.getId();
        } else {
            Enrollment e = new Enrollment();
            e.setStudentId(studentId);
            e.setTeachingClassId(teachingClassId);
            e.setTermId(tc.getTermId());
            e.setStatus(SELECTED);
            e.setScoreStatus("未录入");
            e.setSelectedAt(LocalDateTime.now());
            enrollmentMapper.insert(e);
            enrollmentId = e.getId();
        }

        return new EnrollmentDtos.SelectResult(enrollmentId, "选课成功", List.of());
    }

    /** 学生退课。 */
    @Transactional(rollbackFor = Exception.class)
    public void drop(Long studentId, Long enrollmentId) {
        Enrollment e = enrollmentMapper.selectById(enrollmentId);
        if (e == null || !Objects.equals(e.getStudentId(), studentId)) {
            throw BizException.notFound("选课记录");
        }
        if (DROPPED.equals(e.getStatus())) {
            return;
        }
        if (!Objects.equals(e.getTermId(), currentTermId())) {
            throw new BizException("只能退选当前学期的课程");
        }
        if (e.getScore() != null) {
            throw new BizException("该课程成绩已录入，不能退课");
        }
        TeachingClass tc = teachingClassMapper.selectById(e.getTeachingClassId());
        if (tc != null && !OPEN.equals(tc.getStatus())) {
            throw new BizException("该教学班已" + tc.getStatus() + "，不能退课");
        }

        e.setStatus(DROPPED);
        enrollmentMapper.updateById(e);

        teachingClassMapper.update(null,
                Wrappers.<TeachingClass>lambdaUpdate()
                        .eq(TeachingClass::getId, e.getTeachingClassId())
                        .apply("enrolled > 0")
                        .setSql("enrolled = enrolled - 1"));
    }

    // ------------------------------------------------------------------
    // 成绩
    // ------------------------------------------------------------------

    /** 录入成绩。绩点由 {@link GradePointCalculator} 统一换算。score 为空表示撤销录入。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveScore(Long enrollmentId, BigDecimal score, String scoreStatus) {
        Enrollment e = enrollmentMapper.selectById(enrollmentId);
        if (e == null || DROPPED.equals(e.getStatus())) {
            throw BizException.notFound("选课记录");
        }
        if (score != null && (score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(new BigDecimal("100")) > 0)) {
            throw new BizException("成绩必须在 0 到 100 之间");
        }
        BigDecimal gradePoint = GradePointCalculator.toGradePoint(score);
        String status = score == null
                ? "未录入"
                : (scoreStatus == null || scoreStatus.isBlank() ? "已录入" : scoreStatus);
        // 这里必须显式 set，不能用 updateById：
        // MyBatis-Plus 默认跳过 null 字段，撤销录入时 score 会原地不动，
        // 结果就是「分数还是 88、状态却是未录入」，学分与绩点统计跟着一起错。
        enrollmentMapper.update(null, Wrappers.<Enrollment>lambdaUpdate()
                .eq(Enrollment::getId, enrollmentId)
                .set(Enrollment::getScore, score)
                .set(Enrollment::getGradePoint, gradePoint)
                .set(Enrollment::getScoreStatus, status));
    }

    /**
     * 教师录入成绩：先校验该选课记录属于本人任教的教学班。
     *
     * <p>教师的数据范围仅限本人教学班，所以必须在服务端依据令牌中的 teacherId
     * 校验归属，不能依赖前端只展示自己的班级。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveScoreAsTeacher(Long teacherId, Long enrollmentId,
                                   BigDecimal score, String scoreStatus) {
        Enrollment e = enrollmentMapper.selectById(enrollmentId);
        if (e == null || DROPPED.equals(e.getStatus())) {
            throw BizException.notFound("选课记录");
        }
        TeachingClass tc = teachingClassMapper.selectById(e.getTeachingClassId());
        if (tc == null || !Objects.equals(tc.getTeacherId(), teacherId)) {
            throw BizException.forbidden("只能录入本人任教教学班的成绩");
        }
        saveScore(enrollmentId, score, scoreStatus);
    }

    /** 批量录入成绩，任一条不合法则整批回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public int saveScoresAsTeacher(Long teacherId, List<EnrollmentDtos.ScoreEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new BizException("没有需要保存的成绩");
        }
        for (EnrollmentDtos.ScoreEntry entry : entries) {
            saveScoreAsTeacher(teacherId, entry.enrollmentId(), entry.score(), entry.scoreStatus());
        }
        return entries.size();
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    public Long currentTermId() {
        Term current = termMapper.selectOne(
                Wrappers.<Term>lambdaQuery().eq(Term::getIsCurrent, 1).last("limit 1"));
        return current == null ? null : current.getId();
    }

    private List<EnrollmentDtos.MyCourse> toMyCourses(List<Enrollment> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, TeachingClass> tcMap = loadTeachingClasses(
                list.stream().map(Enrollment::getTeachingClassId).toList());
        Map<Long, Course> courseMap = loadCourses(
                tcMap.values().stream().map(TeachingClass::getCourseId).toList());
        Map<Long, Teacher> teacherMap = loadMap(teacherMapper.selectBatchIds(
                tcMap.values().stream().map(TeachingClass::getTeacherId).distinct().toList()),
                Teacher::getId);
        Map<Long, Term> termMap = loadMap(termMapper.selectBatchIds(
                list.stream().map(Enrollment::getTermId).distinct().toList()), Term::getId);

        List<EnrollmentDtos.MyCourse> result = new ArrayList<>();
        for (Enrollment e : list) {
            TeachingClass tc = tcMap.get(e.getTeachingClassId());
            if (tc == null) {
                continue;
            }
            Course c = courseMap.get(tc.getCourseId());
            Teacher t = teacherMap.get(tc.getTeacherId());
            Term term = termMap.get(e.getTermId());
            result.add(new EnrollmentDtos.MyCourse(
                    e.getId(), tc.getId(), tc.getCode(),
                    c == null ? null : c.getCode(), c == null ? null : c.getName(),
                    c == null ? null : c.getCredit(), c == null ? null : c.getCourseType(),
                    t == null ? null : t.getName(),
                    term == null ? null : term.getName(),
                    tc.getClassroom(), TimeConflictChecker.describe(tc),
                    tc.getWeekday(), tc.getStartSection(), tc.getEndSection(),
                    e.getScore(), e.getScoreStatus(), e.getGradePoint(), e.getStatus()));
        }
        return result;
    }

    private Map<Long, TeachingClass> loadTeachingClasses(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return loadMap(teachingClassMapper.selectBatchIds(distinct), TeachingClass::getId);
    }

    private Map<Long, Course> loadCourses(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return loadMap(courseMapper.selectBatchIds(distinct), Course::getId);
    }

    private Map<Long, Course> loadCoursesFromEnrollments(List<Enrollment> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        Map<Long, TeachingClass> tcMap = loadTeachingClasses(
                list.stream().map(Enrollment::getTeachingClassId).distinct().toList());
        return loadCourses(tcMap.values().stream().map(TeachingClass::getCourseId).toList());
    }

    private static <T> Map<Long, T> loadMap(List<T> list, Function<T, Long> idGetter) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(idGetter, Function.identity(), (x, y) -> x));
    }

    private static String courseName(Map<Long, Course> map, TeachingClass tc) {
        Course c = map.get(tc.getCourseId());
        return c == null ? "未知课程" : c.getName();
    }

    private static List<Long> concat(Long head, List<Long> tail) {
        List<Long> r = new ArrayList<>();
        if (head != null) {
            r.add(head);
        }
        r.addAll(tail);
        return r;
    }
}
