package com.iaas.importer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.UserContext;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.governance.AuditService;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import com.iaas.system.entity.Clazz;
import com.iaas.system.entity.College;
import com.iaas.system.entity.Major;
import com.iaas.system.mapper.ClazzMapper;
import com.iaas.system.mapper.CollegeMapper;
import com.iaas.system.mapper.MajorMapper;
import com.iaas.teacher.entity.Teacher;
import com.iaas.teacher.mapper.TeacherMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 批量导入。
 *
 * <p>教务的数据不是一条条填进来的：课程库、学生名册、教师名册都是教务处
 * 从别的系统导出或者按模板整理好的表格。所以这里做的是"交文件"，
 * 而且分两步走——先校验、再入库。
 *
 * <p>三条约定：
 * <ol>
 *   <li><b>有错不入库</b>：只要有一行不合格就整体不写，避免出现"导了一半"的名册。
 *       校验结果逐行给出，教务改完文件重传即可。</li>
 *   <li><b>按业务键幂等</b>：课程代码、学号、工号是天然主键，
 *       同一份文件重复导入是更新而不是新增，不会把课程库导成两倍。</li>
 *   <li><b>引用要能落地</b>：学生名册里的学院/专业/班级写的是代码，
 *       代码在系统里找不到就报错，而不是悄悄建一条空关联。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ImportService {

    public static final String TYPE_COURSE = "course";
    public static final String TYPE_STUDENT = "student";
    public static final String TYPE_TEACHER = "teacher";

    /** 报告里最多列这么多条错误：再多就该让教务改文件了，不是给他刷屏 */
    private static final int MAX_ERROR_LINES = 50;

    private final SheetReader reader;
    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final ClazzMapper clazzMapper;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // 类型与模板
    // ------------------------------------------------------------------

    public List<ImportDtos.Target> targets() {
        return List.of(
                new ImportDtos.Target(TYPE_COURSE, "课程库",
                        "课程代码是主键，重复导入按代码更新；学分最小单位 0.5",
                        List.of("课程代码", "课程名称", "学分", "学时", "课程性质", "考核方式", "开课学院代码"),
                        List.of("CS301", "编译原理", "3.5", "56", "专业必修", "考试", "CS01")),
                new ImportDtos.Target(TYPE_STUDENT, "学生名册",
                        "学号是主键；学院/专业/班级写代码，系统里没有的代码会报错",
                        List.of("学号", "姓名", "性别", "出生日期", "学院代码", "专业代码", "班级代码",
                                "年级", "联系电话", "邮箱", "学籍状态"),
                        List.of("2023001", "张三", "男", "2005-03-11", "CS01", "CS01", "CS2301",
                                "2023", "13800000000", "zhangsan@example.com", "在读")),
                new ImportDtos.Target(TYPE_TEACHER, "教师名册",
                        "工号是主键；学院代码必须在系统里存在",
                        List.of("工号", "姓名", "性别", "学院代码", "职称", "联系电话", "邮箱", "在职状态"),
                        List.of("t2001", "李四", "女", "CS01", "讲师", "13900000000",
                                "lisi@example.com", "在职")));
    }

    private ImportDtos.Target target(String type) {
        return targets().stream().filter(t -> t.type().equals(type)).findFirst()
                .orElseThrow(() -> new BizException("不支持导入这种数据：" + type));
    }

    // ------------------------------------------------------------------
    // 入口：预览与入库走同一条校验路径，差别只在最后写不写库
    // ------------------------------------------------------------------

    public ImportDtos.Report preview(String type, MultipartFile file) {
        requireStaff();
        return run(type, file, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportDtos.Report commit(String type, MultipartFile file) {
        requireStaff();
        ImportDtos.Report report = run(type, file, true);
        auditService.ingest("批量导入 " + report.label() + "：" + report.fileName()
                + "，共 " + report.total() + " 行，入库 " + report.ok() + " 行", report.ok());
        return report;
    }

    private ImportDtos.Report run(String type, MultipartFile file, boolean commit) {
        ImportDtos.Target target = target(type);
        SheetReader.Table table = reader.read(file);
        List<String> cols = target.columns();
        Map<String, Integer> index = headerIndex(table.header(), cols);

        List<ImportDtos.RowResult> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Runnable> writes = new ArrayList<>();

        int line = 1;
        for (List<String> raw : table.rows()) {
            line++;
            try {
                // 校验通过后把写库动作排队，最后统一执行：
                // 否则校验到第 80 行才发现错误时，前 79 行已经写进库里了
                ImportDtos.RowResult result = switch (type) {
                    case TYPE_COURSE -> courseRow(cols, index, raw, line, writes);
                    case TYPE_STUDENT -> studentRow(cols, index, raw, line, writes);
                    default -> teacherRow(cols, index, raw, line, writes);
                };
                results.add(result);
                // 校验失败是以"结果"的形式返回的，不是抛异常，所以错误清单要在这里收：
                // 漏了这一步，报告会说"2 行失败"却一条原因都不给
                if (!result.ok() && errors.size() < MAX_ERROR_LINES) {
                    errors.add("第 " + line + " 行"
                            + (result.key() == null || result.key().isBlank() ? "" : "（" + result.key() + "）")
                            + "：" + result.message());
                }
            } catch (Exception e) {
                String msg = e instanceof BizException ? e.getMessage() : "该行无法解析";
                results.add(new ImportDtos.RowResult(line, "", false, msg));
                if (errors.size() < MAX_ERROR_LINES) {
                    errors.add("第 " + line + " 行：" + msg);
                }
            }
        }

        long failed = results.stream().filter(r -> !r.ok()).count();
        boolean committed = false;
        if (commit) {
            if (failed > 0) {
                // 有错就不入库：半份名册比没有名册更难收拾
                return new ImportDtos.Report(type, target.label(), fileName(file),
                        results.size(), (int) (results.size() - failed), (int) failed,
                        false, trim(results), errors.isEmpty() ? List.of() : errors);
            }
            writes.forEach(Runnable::run);
            committed = true;
        }
        return new ImportDtos.Report(type, target.label(), fileName(file),
                results.size(), (int) (results.size() - failed), (int) failed,
                committed, trim(results), errors);
    }

    private List<ImportDtos.RowResult> trim(List<ImportDtos.RowResult> rows) {
        List<ImportDtos.RowResult> bad = rows.stream().filter(r -> !r.ok()).toList();
        if (bad.size() <= MAX_ERROR_LINES) {
            return bad;
        }
        return bad.subList(0, MAX_ERROR_LINES);
    }

    /** 表头必须有；顺序不管，缺列直接说缺哪一列。 */
    private Map<String, Integer> headerIndex(List<String> header, List<String> required) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            index.put(header.get(i).trim(), i);
        }
        List<String> missing = required.stream().filter(c -> !index.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new BizException("表头缺少这些列：" + String.join("、", missing)
                    + "（请下载模板对照，表头文字要完全一致）");
        }
        return index;
    }

    // ------------------------------------------------------------------
    // 三种数据的解析与校验
    // ------------------------------------------------------------------

    /** 课程：解析 → 校验 → 排队写库，返回这一行的结果。 */
    private ImportDtos.RowResult courseRow(List<String> cols, Map<String, Integer> index,
                                           List<String> row, int line, List<Runnable> writes) {
        Course c = new Course();
        c.setCode(cell(cols, index, row, "课程代码"));
        c.setName(cell(cols, index, row, "课程名称"));
        c.setCredit(decimal(cell(cols, index, row, "学分"), "学分"));
        c.setHours(integer(cell(cols, index, row, "学时"), "学时"));
        c.setCourseType(cell(cols, index, row, "课程性质"));
        c.setAssessType(cell(cols, index, row, "考核方式"));
        String collegeCode = cell(cols, index, row, "开课学院代码");
        if (!collegeCode.isBlank()) {
            c.setCollegeId(requireCollege(collegeCode));
        }
        c.setStatus(1);
        String problem = null;
        if (c.getCode().isBlank()) problem = "课程代码不能为空";
        else if (c.getName().isBlank()) problem = "课程名称不能为空";
        else if (c.getCredit() == null) problem = "学分不能为空";
        else if (c.getCredit().compareTo(BigDecimal.ZERO) <= 0) problem = "学分必须大于 0";
        // 手册第十四条：学分最小单位是 0.5
        else if (c.getCredit().remainder(new BigDecimal("0.5")).compareTo(BigDecimal.ZERO) != 0) {
            problem = "学分必须是 0.5 的整数倍（学生手册第十四条）";
        } else if (c.getHours() != null && c.getHours() <= 0) {
            problem = "学时必须大于 0";
        }
        if (problem != null) {
            return new ImportDtos.RowResult(line, c.getCode(), false, problem);
        }
        writes.add(() -> upsertCourse(c));
        return new ImportDtos.RowResult(line, c.getCode(), true, "");
    }

    /** 学生：解析 → 校验 → 排队写库。学院/专业/班级的代码必须能在系统里找到。 */
    private ImportDtos.RowResult studentRow(List<String> cols, Map<String, Integer> index,
                                            List<String> row, int line, List<Runnable> writes) {
        Student s = new Student();
        s.setStudentNo(cell(cols, index, row, "学号"));
        s.setName(cell(cols, index, row, "姓名"));
        s.setGender(cell(cols, index, row, "性别"));
        String birth = cell(cols, index, row, "出生日期");
        if (!birth.isBlank()) {
            s.setBirthDate(LocalDate.parse(normalizeDate(birth)));
        }
        s.setCollegeId(requireCollege(cell(cols, index, row, "学院代码")));
        s.setMajorId(requireMajor(cell(cols, index, row, "专业代码")));
        s.setClazzId(requireClazz(cell(cols, index, row, "班级代码")));
        s.setGrade(integer(cell(cols, index, row, "年级"), "年级"));
        s.setPhone(cell(cols, index, row, "联系电话"));
        s.setEmail(cell(cols, index, row, "邮箱"));
        String status = cell(cols, index, row, "学籍状态");
        s.setStatus(status.isBlank() ? "在读" : status);
        String problem = null;
        if (s.getStudentNo().isBlank()) problem = "学号不能为空";
        else if (s.getName().isBlank()) problem = "姓名不能为空";
        else if (s.getGrade() == null) problem = "年级不能为空";
        if (problem != null) {
            return new ImportDtos.RowResult(line, s.getStudentNo(), false, problem);
        }
        writes.add(() -> upsertStudent(s));
        return new ImportDtos.RowResult(line, s.getStudentNo(), true, "");
    }

    private ImportDtos.RowResult teacherRow(List<String> cols, Map<String, Integer> index,
                                            List<String> row, int line, List<Runnable> writes) {
        Teacher t = new Teacher();
        t.setTeacherNo(cell(cols, index, row, "工号"));
        t.setName(cell(cols, index, row, "姓名"));
        String gender = cell(cols, index, row, "性别");
        t.setGender(gender.isBlank() ? "男" : gender);
        t.setCollegeId(requireCollege(cell(cols, index, row, "学院代码")));
        t.setTitle(cell(cols, index, row, "职称"));
        t.setPhone(cell(cols, index, row, "联系电话"));
        t.setEmail(cell(cols, index, row, "邮箱"));
        String status = cell(cols, index, row, "在职状态");
        t.setStatus(status.isBlank() ? "在职" : status);
        String problem = null;
        if (t.getTeacherNo().isBlank()) problem = "工号不能为空";
        else if (t.getName().isBlank()) problem = "姓名不能为空";
        if (problem != null) {
            return new ImportDtos.RowResult(line, t.getTeacherNo(), false, problem);
        }
        writes.add(() -> upsertTeacher(t));
        return new ImportDtos.RowResult(line, t.getTeacherNo(), true, "");
    }

    // ------------------------------------------------------------------
    // 写库：按业务键 upsert
    // ------------------------------------------------------------------

    private void upsertCourse(Course c) {
        Course existing = courseMapper.selectOne(
                Wrappers.<Course>lambdaQuery().eq(Course::getCode, c.getCode()));
        if (existing == null) {
            c.setStatus(c.getStatus() == null ? 1 : c.getStatus());
            courseMapper.insert(c);
        } else {
            c.setId(existing.getId());
            courseMapper.updateById(c);
        }
    }

    private void upsertStudent(Student s) {
        Student existing = studentMapper.selectOne(
                Wrappers.<Student>lambdaQuery().eq(Student::getStudentNo, s.getStudentNo()));
        if (existing == null) {
            studentMapper.insert(s);
        } else {
            s.setId(existing.getId());
            studentMapper.updateById(s);
        }
    }

    private void upsertTeacher(Teacher t) {
        Teacher existing = teacherMapper.selectOne(
                Wrappers.<Teacher>lambdaQuery().eq(Teacher::getTeacherNo, t.getTeacherNo()));
        if (existing == null) {
            teacherMapper.insert(t);
        } else {
            t.setId(existing.getId());
            teacherMapper.updateById(t);
        }
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /** 取值：按表头名找列，找不到就是空串；注意行短于表头时不越界。 */
    private String cell(List<String> header, Map<String, Integer> index, List<String> row, String column) {
        Integer i = index.get(column);
        if (i == null || i >= row.size()) {
            return "";
        }
        return row.get(i) == null ? "" : row.get(i).trim();
    }

    /** Excel 里的日期可能是 2005/3/11、2005.3.11 之类，统一成 ISO。 */
    private String normalizeDate(String raw) {
        String s = raw.replace('/', '-').replace('.', '-').trim();
        String[] parts = s.split("-");
        if (parts.length == 3) {
            return "%s-%02d-%02d".formatted(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        return s;
    }

    private Long requireCollege(String code) {
        if (code.isBlank()) {
            return null;
        }
        College c = collegeMapper.selectOne(Wrappers.<College>lambdaQuery().eq(College::getCode, code));
        if (c == null) {
            throw new BizException("学院代码不存在：" + code);
        }
        return c.getId();
    }

    private Long requireMajor(String code) {
        if (code.isBlank()) {
            return null;
        }
        Major m = majorMapper.selectOne(Wrappers.<Major>lambdaQuery().eq(Major::getCode, code));
        if (m == null) {
            throw new BizException("专业代码不存在：" + code);
        }
        return m.getId();
    }

    private Long requireClazz(String code) {
        if (code.isBlank()) {
            return null;
        }
        Clazz c = clazzMapper.selectOne(Wrappers.<Clazz>lambdaQuery().eq(Clazz::getCode, code));
        if (c == null) {
            throw new BizException("班级代码不存在：" + code);
        }
        return c.getId();
    }

    private BigDecimal decimal(String raw, String what) {
        if (raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(what + "必须是数字，当前是「" + raw + "」");
        }
    }

    private Integer integer(String raw, String what) {
        if (raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim()).intValue();
        } catch (NumberFormatException e) {
            throw new BizException(what + "必须是整数，当前是「" + raw + "」");
        }
    }

    private String fileName(MultipartFile file) {
        return file.getOriginalFilename() == null ? "(未命名文件)" : file.getOriginalFilename();
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可导入数据");
        }
    }

    /** 供前端提示用：把"系统里已有哪些代码"列出来，省得教务猜。 */
    public Map<String, Object> dictionary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("colleges", collegeMapper.selectList(null).stream()
                .collect(Collectors.toMap(College::getCode, College::getName, (a, b) -> a, LinkedHashMap::new)));
        out.put("majors", majorMapper.selectList(null).stream()
                .collect(Collectors.toMap(Major::getCode, Major::getName, (a, b) -> a, LinkedHashMap::new)));
        out.put("clazzes", clazzMapper.selectList(null).stream()
                .collect(Collectors.toMap(Clazz::getCode, Clazz::getName, (a, b) -> a, LinkedHashMap::new)));
        return out;
    }
}
