package com.iaas.teaching;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.TimeConflictChecker;
import com.iaas.system.entity.Major;
import com.iaas.system.mapper.MajorMapper;
import com.iaas.system.entity.Term;
import com.iaas.system.mapper.TermMapper;
import com.iaas.teacher.entity.Teacher;
import com.iaas.teacher.mapper.TeacherMapper;
import com.iaas.teaching.entity.TeachingClass;
import com.iaas.teaching.mapper.TeachingClassMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 教学班（开课）管理。
 *
 * <p>排课时做两重冲突检查：同一教师同一学期时间重叠、同一教室同一学期被占用。
 * 与选课阶段的"学生课表冲突"是两回事：前者约束排课者，后者提示选课者。
 */
@Service
@RequiredArgsConstructor
public class TeachingClassService {

    private static final String OPEN = "开放";

    private final TeachingClassMapper teachingClassMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final TermMapper termMapper;
    private final MajorMapper majorMapper;
    public List<TeachingClassDtos.TeachingClassVO> list(Long termId, Long courseId,
                                                        Long teacherId, boolean onlyOpen) {
        var query = Wrappers.<TeachingClass>lambdaQuery()
                .eq(termId != null, TeachingClass::getTermId, termId)
                .eq(courseId != null, TeachingClass::getCourseId, courseId)
                .eq(teacherId != null, TeachingClass::getTeacherId, teacherId)
                .eq(onlyOpen, TeachingClass::getStatus, OPEN)
                .orderByAsc(TeachingClass::getWeekday)
                .orderByAsc(TeachingClass::getStartSection);
        return toVO(teachingClassMapper.selectList(query));
    }

    /** 学生可选课程列表：指定学期、开放中。 */
    public List<TeachingClassDtos.TeachingClassVO> selectable(Long termId, String keyword) {
        var query = Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTermId, termId)
                .eq(TeachingClass::getStatus, OPEN)
                .orderByAsc(TeachingClass::getWeekday)
                .orderByAsc(TeachingClass::getStartSection);
        List<TeachingClassDtos.TeachingClassVO> vos = toVO(teachingClassMapper.selectList(query));
        if (keyword == null || keyword.isBlank()) {
            return vos;
        }
        String k = keyword.trim();
        return vos.stream()
                .filter(v -> (v.courseName() != null && v.courseName().contains(k))
                        || (v.courseCode() != null && v.courseCode().contains(k))
                        || (v.teacherName() != null && v.teacherName().contains(k)))
                .toList();
    }

    public TeachingClassDtos.TeachingClassVO get(Long id) {
        TeachingClass tc = teachingClassMapper.selectById(id);
        if (tc == null) {
            throw BizException.notFound("教学班");
        }
        return toVO(List.of(tc)).get(0);
    }

    /** 新增或修改。返回本次发现的排课冲突，不阻断保存，由教务判断。 */
    @Transactional(rollbackFor = Exception.class)
    public TeachingClassDtos.SaveResult save(TeachingClassDtos.SaveRequest req) {
        validate(req);
        TeachingClass entity = new TeachingClass();
        entity.setId(req.id());
        entity.setCode(req.code());
        entity.setCourseId(req.courseId());
        entity.setTeacherId(req.teacherId());
        entity.setTermId(req.termId());
        entity.setCapacity(req.capacity() == null ? 60 : req.capacity());
        entity.setWeekday(req.weekday());
        entity.setStartSection(req.startSection());
        entity.setEndSection(req.endSection());
        entity.setStartWeek(req.startWeek() == null ? 1 : req.startWeek());
        entity.setEndWeek(req.endWeek() == null ? 16 : req.endWeek());
        entity.setWeekType(req.weekType() == null ? TimeConflictChecker.WEEK_ALL : req.weekType());
        entity.setClassroom(req.classroom());
        entity.setStatus(req.status() == null ? OPEN : req.status());
        entity.setMajorId(req.majorId());
        entity.setGrade(req.grade());

        if (req.id() == null) {
            if (entity.getCode() == null || entity.getCode().isBlank()) {
                entity.setCode(buildCode(req));
            }
            if (teachingClassMapper.selectCount(Wrappers.<TeachingClass>lambdaQuery()
                    .eq(TeachingClass::getCode, entity.getCode())) > 0) {
                throw new BizException("教学班代码已存在：" + entity.getCode());
            }
            entity.setEnrolled(0);
            teachingClassMapper.insert(entity);
        } else {
            TeachingClass old = teachingClassMapper.selectById(req.id());
            if (old == null) {
                throw BizException.notFound("教学班");
            }
            if (req.capacity() != null && req.capacity() < old.getEnrolled()) {
                throw new BizException("容量不能小于当前已选人数 " + old.getEnrolled());
            }
            teachingClassMapper.updateById(entity);
        }
        return new TeachingClassDtos.SaveResult(entity.getId(), scheduleConflicts(entity));
    }

    /** 删除。已有人选课则拒绝，避免产生孤儿选课记录。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TeachingClass tc = teachingClassMapper.selectById(id);
        if (tc == null) {
            return;
        }
        if (tc.getEnrolled() != null && tc.getEnrolled() > 0) {
            throw new BizException("该教学班已有 " + tc.getEnrolled()
                    + " 人选课，不能删除；可改为「停开」");
        }
        teachingClassMapper.deleteById(id);
    }

    /** 排课冲突检测：教师是否撞课、教室是否被占用。 */
    public List<TeachingClassDtos.ScheduleConflict> scheduleConflicts(TeachingClass target) {
        if (target.getWeekday() == null || target.getStartSection() == null
                || target.getEndSection() == null) {
            return List.of();
        }
        List<TeachingClass> sameTerm = teachingClassMapper.selectList(
                Wrappers.<TeachingClass>lambdaQuery()
                        .eq(TeachingClass::getTermId, target.getTermId())
                        .ne(TeachingClass::getStatus, "停开"));
        Map<Long, Course> courseMap = loadCourses(
                sameTerm.stream().map(TeachingClass::getCourseId).toList());

        List<TeachingClassDtos.ScheduleConflict> conflicts = new ArrayList<>();
        for (TeachingClass other : sameTerm) {
            if (Objects.equals(other.getId(), target.getId())) {
                continue;
            }
            if (!TimeConflictChecker.conflicts(target, other)) {
                continue;
            }
            String label = courseLabel(courseMap, other);
            if (Objects.equals(other.getTeacherId(), target.getTeacherId())) {
                conflicts.add(new TeachingClassDtos.ScheduleConflict(
                        "TEACHER", label, TimeConflictChecker.describe(other), other.getId()));
            }
            if (target.getClassroom() != null && !target.getClassroom().isBlank()
                    && target.getClassroom().equals(other.getClassroom())) {
                conflicts.add(new TeachingClassDtos.ScheduleConflict(
                        "CLASSROOM", label, TimeConflictChecker.describe(other), other.getId()));
            }
        }
        return conflicts;
    }

    public List<TeachingClassDtos.TeachingClassVO> toVO(List<TeachingClass> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Course> courseMap = loadCourses(
                list.stream().map(TeachingClass::getCourseId).toList());
        Map<Long, Teacher> teacherMap = loadMap(teacherMapper.selectBatchIds(
                list.stream().map(TeachingClass::getTeacherId).distinct().toList()), Teacher::getId);
        Map<Long, Term> termMap = loadMap(termMapper.selectBatchIds(
                list.stream().map(TeachingClass::getTermId).distinct().toList()), Term::getId);
        // 面向专业一次批量查，避免每行回查一次
        List<Long> majorIds = list.stream().map(TeachingClass::getMajorId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Major> majorMap = majorIds.isEmpty()
                ? Map.of()
                : loadMap(majorMapper.selectBatchIds(majorIds), Major::getId);

        List<TeachingClassDtos.TeachingClassVO> result = new ArrayList<>();
        for (TeachingClass tc : list) {
            Course c = courseMap.get(tc.getCourseId());
            Teacher t = teacherMap.get(tc.getTeacherId());
            Term term = termMap.get(tc.getTermId());
            Integer remaining = (tc.getCapacity() == null || tc.getEnrolled() == null)
                    ? null : tc.getCapacity() - tc.getEnrolled();
            result.add(new TeachingClassDtos.TeachingClassVO(
                    tc.getId(), tc.getCode(),
                    tc.getCourseId(), c == null ? null : c.getCode(),
                    c == null ? null : c.getName(),
                    c == null ? null : c.getCredit(),
                    c == null ? null : c.getCourseType(),
                    tc.getTeacherId(), t == null ? null : t.getName(),
                    tc.getTermId(), term == null ? null : term.getName(),
                    tc.getCapacity(), tc.getEnrolled(), remaining,
                    tc.getMajorId(), tc.getMajorId() == null ? null
                            : (majorMap.get(tc.getMajorId()) == null ? null
                                    : majorMap.get(tc.getMajorId()).getName()),
                    tc.getGrade(),
                    tc.getWeekday(), tc.getStartSection(), tc.getEndSection(),
                    tc.getStartWeek(), tc.getEndWeek(), tc.getWeekType(),
                    tc.getClassroom(), tc.getStatus(),
                    TimeConflictChecker.describe(tc)));
        }
        return result;
    }

    private void validate(TeachingClassDtos.SaveRequest req) {
        if (req.courseId() == null) {
            throw new BizException("请选择课程");
        }
        if (req.teacherId() == null) {
            throw new BizException("请选择任课教师");
        }
        if (req.termId() == null) {
            throw new BizException("请选择学期");
        }
        if (req.weekday() == null || req.weekday() < 1 || req.weekday() > 7) {
            throw new BizException("星期必须在 1 到 7 之间");
        }
        if (req.startSection() == null || req.endSection() == null
                || req.startSection() < 1 || req.endSection() > 12
                || req.startSection() > req.endSection()) {
            throw new BizException("节次不合法，应为 1-12 且起始节次不大于结束节次");
        }
        int sw = req.startWeek() == null ? 1 : req.startWeek();
        int ew = req.endWeek() == null ? 16 : req.endWeek();
        if (sw < 1 || ew > 30 || sw > ew) {
            throw new BizException("周次不合法，应为 1-30 且起始周不大于结束周");
        }
        if (req.capacity() != null && req.capacity() <= 0) {
            throw new BizException("容量必须大于 0");
        }
    }

    private String buildCode(TeachingClassDtos.SaveRequest req) {
        Term term = termMapper.selectById(req.termId());
        Course course = courseMapper.selectById(req.courseId());
        String termCode = term == null ? String.valueOf(req.termId()) : term.getCode();
        String courseCode = course == null ? String.valueOf(req.courseId()) : course.getCode();
        long seq = teachingClassMapper.selectCount(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTermId, req.termId())
                .eq(TeachingClass::getCourseId, req.courseId())) + 1;
        return "%s-%s-%02d".formatted(termCode, courseCode, seq);
    }

    private Map<Long, Course> loadCourses(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return loadMap(courseMapper.selectBatchIds(distinct), Course::getId);
    }

    private static String courseLabel(Map<Long, Course> map, TeachingClass tc) {
        Course c = map.get(tc.getCourseId());
        return c == null ? tc.getCode() : c.getName() + "（" + tc.getCode() + "）";
    }

    private static <T> Map<Long, T> loadMap(List<T> list, Function<T, Long> idGetter) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(idGetter, Function.identity(), (x, y) -> x));
    }
}
