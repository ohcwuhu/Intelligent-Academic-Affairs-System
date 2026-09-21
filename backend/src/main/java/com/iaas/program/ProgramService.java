package com.iaas.program;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.EnrollmentDtos;
import com.iaas.enrollment.EnrollmentService;
import com.iaas.program.entity.Program;
import com.iaas.program.entity.ProgramCourse;
import com.iaas.program.entity.ProgramModule;
import com.iaas.program.mapper.ProgramCourseMapper;
import com.iaas.program.mapper.ProgramMapper;
import com.iaas.program.mapper.ProgramModuleMapper;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import com.iaas.system.entity.Major;
import com.iaas.system.mapper.MajorMapper;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 培养方案与毕业审核。
 *
 * <p>这一层回答的是学生最关心的那个问题："我还差多少学分能毕业"。
 * 在这之前，问答只能答"以教务处正式审核为准"——因为系统里没有培养方案。
 * 现在有了：方案按模块给出要求学分，审核把学生已通过的课对上计划课程，
 * 逐模块算缺口。
 *
 * <p>两条口径必须说清楚：
 * <ol>
 *   <li><b>只算计划内且已通过的课</b>：方案外的选修课学分不计入模块要求，
 *       单独列出来给教务看，避免"学分够了但结构不对"。</li>
 *   <li><b>课程按名称对齐</b>：培养方案里没有课程代码，只有课程名，
 *       所以先做名称归一化（去空格、括号统一）精确匹配，再退一步做唯一包含匹配
 *       （"数据结构" ↔ "算法与数据结构"）。对不上的课会列出来，不猜。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ProgramService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MISSING_PREVIEW = 12;

    private final ProgramMapper programMapper;
    private final ProgramModuleMapper moduleMapper;
    private final ProgramCourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final MajorMapper majorMapper;
    private final CourseMapper courseLibraryMapper;
    private final EnrollmentService enrollmentService;

    // ------------------------------------------------------------------
    // 方案查询
    // ------------------------------------------------------------------

    public List<ProgramDtos.ProgramRow> list() {
        List<Program> programs = programMapper.selectList(
                Wrappers.<Program>lambdaQuery()
                        .orderByDesc(Program::getImportedAt)
                        .orderByAsc(Program::getMajorName));
        if (programs.isEmpty()) {
            return List.of();
        }
        List<Long> ids = programs.stream().map(Program::getId).toList();
        Map<Long, List<ProgramModule>> modules = moduleMapper.selectList(
                        Wrappers.<ProgramModule>lambdaQuery().in(ProgramModule::getProgramId, ids))
                .stream().collect(Collectors.groupingBy(ProgramModule::getProgramId));
        Map<Long, List<ProgramCourse>> courses = courseMapper.selectList(
                        Wrappers.<ProgramCourse>lambdaQuery().in(ProgramCourse::getProgramId, ids))
                .stream().collect(Collectors.groupingBy(ProgramCourse::getProgramId));
        return programs.stream()
                .map(p -> toRow(p,
                        modules.getOrDefault(p.getId(), List.of()),
                        courses.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    public ProgramDtos.Detail detail(Long id) {
        Program p = programMapper.selectById(id);
        if (p == null) {
            throw BizException.notFound("培养方案");
        }
        List<ProgramModule> modules = moduleMapper.selectList(
                Wrappers.<ProgramModule>lambdaQuery()
                        .eq(ProgramModule::getProgramId, id)
                        .orderByAsc(ProgramModule::getSortNo));
        List<ProgramCourse> courses = courseMapper.selectList(
                Wrappers.<ProgramCourse>lambdaQuery()
                        .eq(ProgramCourse::getProgramId, id)
                        .orderByAsc(ProgramCourse::getModule)
                        .orderByAsc(ProgramCourse::getTermNo)
                        .orderByAsc(ProgramCourse::getId));
        // 计划课程对齐到的课程码：教务要能一眼看出"这门课对到了哪个编号"
        Map<Long, Course> matched = fetchCourses(courses.stream()
                .map(ProgramCourse::getCourseId).filter(Objects::nonNull).distinct().toList());
        return new ProgramDtos.Detail(toRow(p, modules, courses),
                modules.stream().map(m -> new ProgramDtos.ModuleRow(
                        m.getCategory(), m.getHoursText(), m.getCredit(), m.getRatio())).toList(),
                courses.stream().map(c -> toCourseRow(c, matched)).toList());
    }

    private Map<Long, Course> fetchCourses(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return courseLibraryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (x, y) -> x));
    }

    /** 某个专业的现行方案：审核用它当基准。 */
    public Program currentForMajor(Long majorId) {
        if (majorId == null) {
            return null;
        }
        return programMapper.selectOne(Wrappers.<Program>lambdaQuery()
                .eq(Program::getMajorId, majorId)
                .eq(Program::getStatus, "现行")
                .orderByDesc(Program::getImportedAt)
                .last("limit 1"));
    }

    // ------------------------------------------------------------------
    // 毕业审核
    // ------------------------------------------------------------------

    public ProgramDtos.Audit audit(Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw BizException.notFound("学生");
        }
        Program program = currentForMajor(student.getMajorId());
        Major major = student.getMajorId() == null ? null : majorMapper.selectById(student.getMajorId());
        String majorName = major == null ? null : major.getName();
        if (program == null) {
            return new ProgramDtos.Audit(student.getId(), student.getStudentNo(), student.getName(),
                    null, null, majorName, student.getGrade(),
                    null, null, null, false, List.of(), List.of(),
                    List.of("系统里还没有这个专业的现行培养方案，无法给出学分缺口；"
                            + "教务可在「数据导入」里导入培养方案后重试。"));
        }

        List<ProgramModule> modules = moduleMapper.selectList(
                Wrappers.<ProgramModule>lambdaQuery()
                        .eq(ProgramModule::getProgramId, program.getId())
                        .orderByAsc(ProgramModule::getSortNo));
        List<ProgramCourse> courses = courseMapper.selectList(
                Wrappers.<ProgramCourse>lambdaQuery().eq(ProgramCourse::getProgramId, program.getId()));

        // 学生已通过的课（成绩 ≥ 60），按课程名归一化后索引
        List<EnrollmentDtos.MyCourse> mine = enrollmentService.myCourses(studentId, null);
        Map<String, EnrollmentDtos.MyCourse> passedByName = new LinkedHashMap<>();
        List<EnrollmentDtos.MyCourse> passed = new ArrayList<>();
        for (EnrollmentDtos.MyCourse c : mine) {
            if (c.score() != null && c.score().compareTo(new BigDecimal("60")) >= 0) {
                passed.add(c);
                passedByName.putIfAbsent(normalize(c.courseName()), c);
            }
        }

        Set<Long> matchedCourseIds = new HashSet<>();
        List<String> notes = new ArrayList<>();
        List<ProgramDtos.ModuleAudit> moduleAudits = new ArrayList<>();

        for (ProgramModule m : modules) {
            List<ProgramCourse> inModule = courses.stream()
                    .filter(c -> Objects.equals(c.getModule(), m.getCategory()))
                    .toList();
            BigDecimal earned = BigDecimal.ZERO;
            int passedCount = 0;
            List<String> missing = new ArrayList<>();
            for (ProgramCourse pc : inModule) {
                EnrollmentDtos.MyCourse hit = match(pc.getCourseName(), passed, passedByName);
                if (hit != null) {
                    earned = earned.add(pc.getCredit());
                    passedCount++;
                    matchedCourseIds.add(hit.enrollmentId());
                } else if (missing.size() < MISSING_PREVIEW) {
                    missing.add(pc.getCourseName());
                }
            }
            BigDecimal required = m.getCredit() == null ? BigDecimal.ZERO : m.getCredit();
            BigDecimal gap = required.subtract(earned).max(BigDecimal.ZERO);
            moduleAudits.add(new ProgramDtos.ModuleAudit(
                    m.getCategory(), required, earned, gap,
                    inModule.size(), passedCount, missing));
        }

        BigDecimal earnedTotal = moduleAudits.stream()
                .map(ProgramDtos.ModuleAudit::earned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal min = program.getMinCredit() == null ? BigDecimal.ZERO : program.getMinCredit();
        BigDecimal gap = min.subtract(earnedTotal).max(BigDecimal.ZERO);

        // 修了但不在本方案计划内的课：单独列出来，因为"学分够了结构不对"也很常见
        List<ProgramDtos.CourseRow> outside = passed.stream()
                .filter(c -> !matchedCourseIds.contains(c.enrollmentId()))
                .map(c -> new ProgramDtos.CourseRow(null, "方案外", null, c.courseName(), "理论",
                        null, c.credit(), null, null, null, null, null,
                        c.termName() + " 成绩 " + c.score(), null, null, c.courseCode()))
                .toList();

        long unmatchedPlan = courses.stream()
                .filter(pc -> match(pc.getCourseName(), passed, passedByName) == null)
                .count();
        notes.add("已获学分 " + earnedTotal + "，毕业最低 " + min + "；"
                + "课程按名称与培养计划对齐（方案里没有课程代码），未对上的计划课程 "
                + unmatchedPlan + " 门，未计入已获学分");
        if (!outside.isEmpty()) {
            notes.add("另有 " + outside.size() + " 门已通过的课不在本方案计划内，未计入模块学分");
        }
        notes.add("最终毕业结论以教务处审核为准；本结论按现行培养方案计算");

        return new ProgramDtos.Audit(student.getId(), student.getStudentNo(), student.getName(),
                program.getId(), program.getTitle(), program.getMajorName(), program.getGrade(),
                min, earnedTotal, gap, gap.compareTo(BigDecimal.ZERO) == 0,
                moduleAudits, outside, notes);
    }

    /**
     * 把计划课程对上已通过的课。
     *
     * <p>先精确匹配（归一化后全等），再退一步做唯一包含匹配：
     * "数据结构" 与 "算法与数据结构" 应当能对上，但如果有两门都包含它，
     * 就不猜，留给人看。
     */
    private EnrollmentDtos.MyCourse match(String planName, List<EnrollmentDtos.MyCourse> passed,
                                          Map<String, EnrollmentDtos.MyCourse> passedByName) {
        String key = normalize(planName);
        EnrollmentDtos.MyCourse exact = passedByName.get(key);
        if (exact != null) {
            return exact;
        }
        List<EnrollmentDtos.MyCourse> candidates = passed.stream()
                .filter(c -> {
                    String n = normalize(c.courseName());
                    return n.length() >= 4 && (n.contains(key) || key.contains(n));
                })
                .toList();
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /** 归一化：去空格、全角括号转半角、去掉"专业"这类无用后缀影响。 */
    static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.replace(" ", "").replace("\u3000", "")
                .replace('（', '(').replace('）', ')')
                .replace("Ⅱ", "II").toLowerCase();
    }

    private ProgramDtos.ProgramRow toRow(Program p, List<ProgramModule> modules,
                                         List<ProgramCourse> courses) {
        // 学分只累加"毕业要求结构里那几个模块"的课：
        // 辅修模块之类的课不在结构表里，算进去只会让人以为计划学分有 276
        Set<String> categories = modules.stream().map(ProgramModule::getCategory)
                .collect(Collectors.toSet());
        BigDecimal sum = courses.stream()
                .filter(c -> categories.contains(c.getModule()))
                .map(ProgramCourse::getCredit)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProgramDtos.ProgramRow(
                p.getId(), p.getTitle(), p.getMajorId(), p.getMajorName(), p.getGrade(),
                p.getDegree(), p.getDuration(), p.getMinCredit(), p.getSourceNote(), p.getStatus(),
                p.getImportedAt() == null ? null : p.getImportedAt().format(TS),
                modules.size(), courses.size(), sum);
    }

    private static ProgramDtos.CourseRow toCourseRow(ProgramCourse c, Map<Long, Course> matched) {
        Course lib = c.getCourseId() == null ? null : matched.get(c.getCourseId());
        return new ProgramDtos.CourseRow(
                c.getId(), c.getModule(), c.getGroupName(), c.getCourseName(), c.getCourseType(),
                c.getAssessType(), c.getCredit(), c.getTotalHours(), c.getLabHours(),
                c.getComputerHours(), c.getTermNo(), c.getWeekHours(), c.getNote(),
                c.getRequired(), c.getCourseId(), lib == null ? null : lib.getCode());
    }
}
