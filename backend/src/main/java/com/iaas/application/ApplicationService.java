package com.iaas.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iaas.application.entity.StudentApplication;
import com.iaas.application.mapper.StudentApplicationMapper;
import com.iaas.common.BizException;
import com.iaas.common.PageResult;
import com.iaas.common.UserContext;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.EnrollmentDtos;
import com.iaas.enrollment.EnrollmentService;
import com.iaas.enrollment.TimeConflictChecker;
import com.iaas.governance.AuditService;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import com.iaas.system.entity.Clazz;
import com.iaas.system.entity.Major;
import com.iaas.system.entity.Term;
import com.iaas.system.mapper.ClazzMapper;
import com.iaas.system.mapper.CollegeMapper;
import com.iaas.system.mapper.MajorMapper;
import com.iaas.system.mapper.TermMapper;
import com.iaas.system.entity.College;
import com.iaas.teaching.entity.TeachingClass;
import com.iaas.teaching.mapper.TeachingClassMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 办事与审批。
 *
 * <p>这一层解决的正是"规章问答之后呢"：系统能解释免听、重修、转专业的条件，
 * 学生看完还是得填一张单子交到教务处。把这一步也放进系统，闭环才完整：
 * 规则问答 → 提交申请 → 系统先做机械可判的预检 → 教务审批 → 全过程留痕。
 *
 * <p>预检只做系统能判准的部分（有没有冲突、绩点够不够、是不是转第二次），
 * 判断不了的（比如"确有特殊困难"）留给人，预检结论写进单据备注供审批人参考。
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    public static final String TYPE_ON_EXEMPT = "ON_EXEMPT";
    public static final String TYPE_RETAKE = "RETAKE";
    public static final String TYPE_TRANSFER_MAJOR = "TRANSFER_MAJOR";
    public static final String TYPE_CERTIFICATE = "CERTIFICATE";
    public static final String TYPE_MAKEUP = "MAKEUP";
    public static final String TYPE_SCORE_RETRY = "SCORE_RETRY";
    public static final String TYPE_ENGLISH_SUB = "ENGLISH_SUB";
    public static final String TYPE_INNOVATION = "INNOVATION_CREDIT";
    public static final String TYPE_VETERAN = "VETERAN_EXEMPT";
    public static final String TYPE_BANK_ACCOUNT = "BANK_ACCOUNT";
    public static final String TYPE_MAJOR_DIRECTION = "MAJOR_DIRECTION";
    public static final String TYPE_MINOR = "MINOR";
    public static final String TYPE_CLASSROOM = "CLASSROOM";

    private static final Map<String, String> TYPE_TEXT = new LinkedHashMap<>();

    static {
        TYPE_TEXT.put(TYPE_ON_EXEMPT, "免听/间听申请");
        TYPE_TEXT.put(TYPE_RETAKE, "重新修读申请");
        TYPE_TEXT.put(TYPE_TRANSFER_MAJOR, "转专业申请");
        TYPE_TEXT.put(TYPE_CERTIFICATE, "证明打印申请");
        TYPE_TEXT.put(TYPE_MAKEUP, "低年级课程补修");
        TYPE_TEXT.put(TYPE_SCORE_RETRY, "刷分重新修读");
        TYPE_TEXT.put(TYPE_ENGLISH_SUB, "英语替换修读");
        TYPE_TEXT.put(TYPE_INNOVATION, "创新创业学分认定");
        TYPE_TEXT.put(TYPE_VETERAN, "退伍学生课程免修");
        TYPE_TEXT.put(TYPE_BANK_ACCOUNT, "银行账号变更");
        TYPE_TEXT.put(TYPE_MAJOR_DIRECTION, "专业方向申请");
        TYPE_TEXT.put(TYPE_MINOR, "辅修申请");
        TYPE_TEXT.put(TYPE_CLASSROOM, "教室借用申请");
    }

    /** 需要填材料才受理的事项：没有证明材料，教务没法判。 */
    private static final Map<String, String> MATERIAL_REQUIRED = Map.of(
            TYPE_INNOVATION, "请写明获奖证书/论文/技能证书的名称与取得时间",
            TYPE_VETERAN, "请写明退伍证编号与退役时间",
            TYPE_BANK_ACCOUNT, "请写明本人银行账号与开户行（变更原因要写清）");

    /** 对象是文本而不是系统记录的事项。 */
    private static final java.util.Set<String> TEXT_TARGET_TYPES = java.util.Set.of(
            TYPE_CERTIFICATE, TYPE_ENGLISH_SUB, TYPE_INNOVATION, TYPE_VETERAN,
            TYPE_BANK_ACCOUNT, TYPE_MAJOR_DIRECTION);

    private static final String PENDING = "待审";
    private static final String APPROVED = "已通过";
    private static final String REJECTED = "已驳回";
    private static final String WITHDRAWN = "已撤回";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StudentApplicationMapper mapper;
    private final StudentMapper studentMapper;
    private final TermMapper termMapper;
    private final CourseMapper courseMapper;
    private final MajorMapper majorMapper;
    private final ClazzMapper clazzMapper;
    private final CollegeMapper collegeMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final EnrollmentService enrollmentService;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // 学生侧
    // ------------------------------------------------------------------

    /** 申请类型清单，格式为 编码|名称，前端直接用来渲染下拉项。 */
    public List<String> types() {
        return TYPE_TEXT.entrySet().stream()
                .map(e -> e.getKey() + "|" + e.getValue())
                .toList();
    }

    /** 提交表单的可选项，按类型取不同的对象集合。 */
    public List<ApplicationDtos.Option> options(String type) {
        Long studentId = UserContext.require().requireStudentId();
        Long termId = enrollmentService.currentTermId();
        if (TYPE_RETAKE.equals(type)) {
            return mapper.failedCourses(studentId, termId).stream()
                    .map(f -> new ApplicationDtos.Option(f.getCourseId(),
                            f.getCourseCode() + " " + f.getCourseName(),
                            "以往学期最高 " + num(f.getBestScore()) + " 分，未取得学分"))
                    .toList();
        }
        if (TYPE_SCORE_RETRY.equals(type)) {
            // 刷分的前提是"已经通过"：没通过的课该走重修，不是刷分
            return mapper.passedCourses(studentId, termId).stream()
                    .map(f -> new ApplicationDtos.Option(f.getCourseId(),
                            f.getCourseCode() + " " + f.getCourseName(),
                            "已通过，最高 " + num(f.getBestScore()) + " 分"))
                    .toList();
        }
        if (TYPE_MAKEUP.equals(type)) {
            // 补修：跟着低年级再修一次，通常是转专业/插班后补修低年级课程
            return mapper.failedCourses(studentId, termId).stream()
                    .map(f -> new ApplicationDtos.Option(f.getCourseId(),
                            f.getCourseCode() + " " + f.getCourseName(),
                            "以往未通过，可申请随低年级补修"))
                    .toList();
        }
        if (TYPE_TRANSFER_MAJOR.equals(type) || TYPE_MINOR.equals(type)) {
            return majorMapper.selectList(
                            Wrappers.<Major>lambdaQuery().orderByAsc(Major::getCode)).stream()
                    .map(m -> new ApplicationDtos.Option(m.getId(),
                            m.getCode() + " " + m.getName(),
                            TYPE_MINOR.equals(type) ? "按辅修培养计划执行" : "转入后按新专业培养计划执行"))
                    .toList();
        }
        if (TYPE_ON_EXEMPT.equals(type)) {
            return enrollmentService.myCourses(studentId, termId).stream()
                    .map(c -> new ApplicationDtos.Option(c.teachingClassId(),
                            c.courseName() + "（" + c.courseCode() + "）",
                            c.timeText() + "　" + (c.classroom() == null ? "地点待定" : c.classroom())))
                    .toList();
        }
        return List.of();
    }

    @Transactional(rollbackFor = Exception.class)
    public ApplicationDtos.SubmitResult submit(ApplicationDtos.SubmitRequest req) {
        UserContext.Principal me = UserContext.require();
        Long studentId = me.requireStudentId();
        String type = req.type() == null ? "" : req.type().trim();
        if (!TYPE_TEXT.containsKey(type)) {
            throw new BizException("申请类型不合法");
        }
        String reason = req.reason() == null ? "" : req.reason().strip();
        if (reason.length() < 5) {
            throw new BizException("请把申请理由写清楚一点（至少 5 个字），教务处要据此判断");
        }
        String target = req.target() == null ? "" : req.target().strip();
        // 这些事项的对象不是系统里的记录（证明名称、替换方式、银行账号、教室与时段），
        // 所以要求填文本；其余类型的对象必须从系统给出的候选里选
        if (TEXT_TARGET_TYPES.contains(type)) {
            if (target.isBlank()) {
                throw new BizException(switch (type) {
                    case TYPE_CERTIFICATE -> "请写明要打印哪种证明";
                    case TYPE_ENGLISH_SUB -> "请写明替换方式，例如「用雅思 6.0 替换大学英语（四）」";
                    case TYPE_BANK_ACCOUNT -> "请写明新的银行账号与开户行";
                    case TYPE_MAJOR_DIRECTION -> "请写明要选的专业方向";
                    case TYPE_INNOVATION -> "请写明认定的项目名称";
                    case TYPE_VETERAN -> "请写明免修的课程";
                    default -> "请写明借用的教室与时段";
                });
            }
        } else if (!TYPE_CLASSROOM.equals(type) && req.targetId() == null) {
            throw new BizException("请选择要申请的对象");
        }
        // 教室借用的对象由时段拼出来，学生不用自己写一遍
        if (TYPE_CLASSROOM.equals(type) && target.isBlank()) {
            target = "%s %s 第%d-%d节 %s".formatted(
                    req.roomName() == null ? "" : req.roomName(),
                    weekdayText(req.roomWeekday()),
                    req.roomStartSection() == null ? 0 : req.roomStartSection(),
                    req.roomEndSection() == null ? 0 : req.roomEndSection(),
                    req.roomWeeks() == null ? "" : req.roomWeeks()).trim();
        }
        if (MATERIAL_REQUIRED.containsKey(type)
                && (req.materials() == null || req.materials().isBlank())) {
            throw new BizException(MATERIAL_REQUIRED.get(type));
        }

        Long termId = enrollmentService.currentTermId();
        String precheck = precheck(type, studentId, termId, req);

        // 同一件事重复提交会变成两张单子压在教务处手里，先拦住
        Long dup = mapper.selectCount(Wrappers.<StudentApplication>lambdaQuery()
                .eq(StudentApplication::getStudentId, studentId)
                .eq(StudentApplication::getTermId, termId)
                .eq(StudentApplication::getType, type)
                .eq(StudentApplication::getStatus, PENDING)
                .eq(req.targetId() != null, StudentApplication::getTargetId, req.targetId()));
        if (dup != null && dup > 0) {
            throw new BizException("同一事项已经提交过，请等教务处处理完再提");
        }

        StudentApplication a = new StudentApplication();
        a.setType(type);
        a.setStudentId(studentId);
        a.setTermId(termId);
        a.setTargetId(req.targetId());
        a.setTarget(target.isBlank() ? TYPE_TEXT.get(type) : target);
        a.setReason(reason);
        a.setMaterials(req.materials() == null ? "" : req.materials().strip());
        a.setRoomName(req.roomName());
        a.setRoomWeekday(req.roomWeekday());
        a.setRoomStartSection(req.roomStartSection());
        a.setRoomEndSection(req.roomEndSection());
        a.setRoomWeeks(req.roomWeeks());
        a.setStatus(PENDING);
        a.setPrecheckNote(precheck);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        mapper.insert(a);

        auditService.workflow("APPLY", TYPE_TEXT.get(type) + "：" + a.getTarget(),
                "学生提交申请：" + reason);
        return new ApplicationDtos.SubmitResult(a.getId(), a.getStatus(),
                "已提交，等待教务处审批", precheck);
    }

    public List<ApplicationDtos.Row> mine() {
        Long studentId = UserContext.require().requireStudentId();
        List<StudentApplication> list = mapper.selectList(
                Wrappers.<StudentApplication>lambdaQuery()
                        .eq(StudentApplication::getStudentId, studentId)
                        .orderByDesc(StudentApplication::getCreatedAt));
        return toRows(list);
    }

    /** 撤回：只有本人的、还在待审的单子能撤。 */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        UserContext.Principal me = UserContext.require();
        StudentApplication a = mapper.selectById(id);
        if (a == null || !Objects.equals(a.getStudentId(), me.requireStudentId())) {
            throw BizException.notFound("申请单");
        }
        if (!PENDING.equals(a.getStatus())) {
            throw new BizException("只有待审的申请可以撤回，当前状态：" + a.getStatus());
        }
        a.setStatus(WITHDRAWN);
        a.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(a);
        auditService.workflow("APPLY", TYPE_TEXT.getOrDefault(a.getType(), a.getType()) + "：撤回",
                "学生撤回申请单 #" + id);
    }

    // ------------------------------------------------------------------
    // 教务侧
    // ------------------------------------------------------------------

    /**
     * 出具证明。
     *
     * <p>只有"已通过"的证明打印申请能出成品——没审过就出证明，等于绕开了审批。
     * 系统负责把内容排成可打印的一页，盖章仍然必须到教务处，
     * 所以成品里明确写了"打印后到教务处盖章"。
     */
    public ApplicationDtos.Certificate certificate(Long applicationId) {
        UserContext.Principal me = UserContext.require();
        StudentApplication a = mapper.selectById(applicationId);
        if (a == null) {
            throw BizException.notFound("申请单");
        }
        boolean self = me.isStudent() && Objects.equals(a.getStudentId(), me.refId());
        if (!self && !me.isStaff()) {
            throw BizException.forbidden("只能查看本人申请的证明");
        }
        if (!TYPE_CERTIFICATE.equals(a.getType())) {
            throw new BizException("这张单子不是证明打印申请");
        }
        if (!APPROVED.equals(a.getStatus())) {
            throw new BizException("证明要等教务处审批通过后才能打印，当前状态：" + a.getStatus());
        }
        Student s = studentMapper.selectById(a.getStudentId());
        Major major = s == null || s.getMajorId() == null ? null : majorMapper.selectById(s.getMajorId());
        Clazz clazz = s == null || s.getClazzId() == null ? null : clazzMapper.selectById(s.getClazzId());
        College college = s == null || s.getCollegeId() == null
                ? null : collegeMapper.selectById(s.getCollegeId());
        Term term = a.getTermId() == null ? null : termMapper.selectById(a.getTermId());

        String certName = a.getTarget() == null ? "在读证明" : a.getTarget();
        boolean scoreKind = certName.contains("成绩");
        List<String> lines = new ArrayList<>();
        String summary = null;
        if (scoreKind) {
            EnrollmentDtos.CreditSummary credit = enrollmentService.creditSummary(a.getStudentId());
            summary = "已获学分 " + credit.earnedCredit() + "，在修学分 " + credit.inProgressCredit()
                    + "，平均学分绩点 " + credit.gpa();
            for (EnrollmentDtos.MyCourse c : enrollmentService.myCourses(a.getStudentId(), null)) {
                if (c.score() != null) {
                    lines.add(c.courseName() + "（" + c.courseCode() + "）　"
                            + c.score() + " 分　" + c.credit() + " 学分");
                }
            }
        } else {
            lines.add("兹证明 " + (s == null ? "" : s.getName()) + "（学号 "
                    + (s == null ? "" : s.getStudentNo()) + "）系我院 "
                    + (college == null ? "" : college.getName()) + " "
                    + (major == null ? "" : major.getName()) + " 专业 "
                    + (clazz == null ? "" : clazz.getName()) + " 学生，"
                    + (s == null || s.getGrade() == null ? "" : s.getGrade() + " 级") + "，"
                    + "现为在读学生。");
        }
        List<String> notes = new ArrayList<>();
        notes.add("本证明由教务系统生成，打印后须到教务处盖章方可对外使用");
        notes.add("如需英文版或其它格式，请到教务处现场办理");

        return new ApplicationDtos.Certificate(
                "C%06d".formatted(a.getId()), certName, scoreKind ? "成绩证明" : "在读证明",
                s == null ? null : s.getName(), s == null ? null : s.getStudentNo(),
                s == null ? null : s.getGender(),
                college == null ? null : college.getName(),
                major == null ? null : major.getName(),
                clazz == null ? null : clazz.getName(),
                s == null ? null : s.getGrade(),
                java.time.LocalDate.now().toString(),
                term == null ? null : term.getName(),
                summary, lines, notes);
    }

    public PageResult<ApplicationDtos.Row> page(long page, long size, String status, String type) {
        var q = Wrappers.<StudentApplication>lambdaQuery()
                .eq(status != null && !status.isBlank(), StudentApplication::getStatus, status)
                .eq(type != null && !type.isBlank(), StudentApplication::getType, type)
                // 待审（reviewedAt 为空）排最前，其次按提交时间倒序
                .orderByAsc(StudentApplication::getReviewedAt)
                .orderByDesc(StudentApplication::getCreatedAt);
        IPage<StudentApplication> p = mapper.selectPage(new Page<>(page, size), q);
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), toRows(p.getRecords()));
    }

    @Transactional(rollbackFor = Exception.class)
    public ApplicationDtos.Row review(Long id, ApplicationDtos.ReviewRequest req) {
        UserContext.Principal me = UserContext.require();
        StudentApplication a = mapper.selectById(id);
        if (a == null) {
            throw BizException.notFound("申请单");
        }
        if (!PENDING.equals(a.getStatus())) {
            throw new BizException("这张单子已经处理过了，当前状态：" + a.getStatus());
        }
        boolean approve = "APPROVE".equalsIgnoreCase(req.action());
        boolean reject = "REJECT".equalsIgnoreCase(req.action());
        if (!approve && !reject) {
            throw new BizException("审批动作只能是 APPROVE 或 REJECT");
        }
        String note = req.note() == null ? "" : req.note().strip();
        if (reject && note.isBlank()) {
            throw new BizException("驳回要写清理由，学生据此才知道下一步怎么办");
        }
        a.setStatus(approve ? APPROVED : REJECTED);
        a.setReviewer(me.realName() == null ? me.username() : me.realName());
        a.setReviewNote(note);
        a.setReviewedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(a);

        auditService.workflow("REVIEW",
                TYPE_TEXT.getOrDefault(a.getType(), a.getType()) + "：" + a.getTarget(),
                (approve ? "通过" : "驳回") + "申请单 #" + id + (note.isBlank() ? "" : "：" + note));
        return toRows(List.of(a)).get(0);
    }

    // ------------------------------------------------------------------
    // 预检：系统能判准的先判，判不了的留给审批人
    // ------------------------------------------------------------------

    private String precheck(String type, Long studentId, Long termId, ApplicationDtos.SubmitRequest req) {
        Long targetId = req.targetId();
        List<String> notes = new ArrayList<>();
        switch (type) {
            case TYPE_MAKEUP -> {
                if (!failedCourse(studentId, termId, targetId)) {
                    throw new BizException("这门课你没有未通过的记录，不需要补修");
                }
                notes.add("以往学期有未通过的记录，符合补修前提");
                notes.add("补修要跟随低年级教学班上课；时间冲突时按学生手册第十九条申请免听/间听");
            }
            case TYPE_SCORE_RETRY -> {
                if (!passedCourse(studentId, termId, targetId)) {
                    throw new BizException("这门课你还没有通过，不能刷分；请改提「重新修读」申请（学生手册第二十三条）");
                }
                notes.add("这门课已经通过，属于刷分重新修读");
                notes.add("成绩按多次考试的最高一次记载，学生手册第二十三条");
            }
            case TYPE_ENGLISH_SUB -> notes.add("英语替换修读按学院英语教学改革的口径审核，需教务处确认替换关系");
            case TYPE_INNOVATION -> {
                notes.add("奖励学分可用于申请免修免考任意选修课，学生手册第十九条");
                notes.add("材料由教务处核对原件，认定结果以教务处为准");
            }
            case TYPE_VETERAN -> notes.add("退伍学生课程免修按学院退伍复学管理办法执行，材料需核验原件");
            case TYPE_BANK_ACCOUNT -> notes.add("账号变更直接影响退费与补助发放，提交后由财务与教务处各核一次");
            case TYPE_MAJOR_DIRECTION -> notes.add("专业方向在培养方案里是二选一，一经确定不再随意变更");
            case TYPE_MINOR -> notes.add("辅修按第二专业培养计划执行，需满足开课人数要求");
            case TYPE_CLASSROOM -> {
                if (req.roomName() == null || req.roomName().isBlank() || req.roomWeekday() == null
                        || req.roomStartSection() == null || req.roomEndSection() == null) {
                    throw new BizException("请选择教室与使用时段（星期、节次）");
                }
                if (req.roomStartSection() > req.roomEndSection()) {
                    throw new BizException("起始节次不能大于结束节次");
                }
                // 教室借用的预检是真正有用的一条：这个时段这间教室是否已经被排课占了
                List<TeachingClass> busy = teachingClassMapper.selectList(
                        Wrappers.<TeachingClass>lambdaQuery()
                                .eq(TeachingClass::getTermId, termId)
                                .eq(TeachingClass::getClassroom, req.roomName())
                                .eq(TeachingClass::getWeekday, req.roomWeekday())
                                .ne(TeachingClass::getStatus, "停开"));
                for (TeachingClass tc : busy) {
                    boolean overlap = tc.getStartSection() <= req.roomEndSection()
                            && req.roomStartSection() <= tc.getEndSection();
                    if (overlap) {
                        Course c = courseMapper.selectById(tc.getCourseId());
                        throw new BizException("该时段这间教室已被占用："
                                + (c == null ? tc.getCode() : c.getName()) + "（" + tc.getCode() + "，"
                                + TimeConflictChecker.describe(tc) + "）。请换时段或换教室。");
                    }
                }
                notes.add("该时段这间教室当前没有排课占用，提交后由教务处确认");
            }
            case TYPE_ON_EXEMPT -> {
                // 必须"这门课"就在冲突里，而不是"本学期有别的冲突"：
                // 免听/间听是针对具体课程的申请，拿一门不冲突的课来申请站不住脚
                boolean clash = enrollmentService.hasTimeClash(studentId, termId, targetId);
                if (!clash) {
                    throw new BizException("这门课与你本学期的其它选课没有时间冲突。"
                            + "免听/间听只用于解决冲突（学生手册第十九条），没有冲突就不需要申请。");
                }
                TeachingClass tc = teachingClassMapper.selectById(targetId);
                notes.add("这门课与已选课程存在时间冲突，符合免听/间听的申请前提");
                if (tc != null) {
                    notes.add("申请课程：" + TimeConflictChecker.describe(tc));
                }
                Integer week = teachingWeek(termId);
                if (week != null && week > 1) {
                    notes.add("现在是第 " + week + " 教学周，手册第十九条要求在开课第一周内提出，需教务处特批");
                } else if (week != null) {
                    notes.add("当前第 " + week + " 教学周，在手册要求的第一周内");
                }
            }
            case TYPE_RETAKE -> {
                boolean failed = mapper.failedCourses(studentId, termId).stream()
                        .anyMatch(f -> Objects.equals(f.getCourseId(), targetId));
                if (!failed) {
                    throw new BizException("这门课你没有未通过的记录，"
                            + "不符合重新修读条件（学生手册第二十三条）");
                }
                Course c = courseMapper.selectById(targetId);
                notes.add("以往学期有未通过记录，符合重新修读条件");
                if (c != null) {
                    notes.add("重修需按规定缴交重修费用，未按时选课或缴费将取消资格");
                }
                if (Boolean.TRUE.equals(enrollmentService.hasTimeClashForCourse(studentId, termId, targetId))) {
                    notes.add("重修课程与已有课表存在时间冲突，可在本单通过后申请免听/间听");
                }
            }
            case TYPE_TRANSFER_MAJOR -> {
                EnrollmentDtos.CreditSummary summary = enrollmentService.creditSummary(studentId);
                if (summary.gpa() != null && summary.gpa().compareTo(new BigDecimal("1.0")) < 0) {
                    throw new BizException("原专业已修课程平均学分绩点低于 1.0，"
                            + "按学生手册第二十九条不准予转专业");
                }
                Long transferred = mapper.selectCount(Wrappers.<StudentApplication>lambdaQuery()
                        .eq(StudentApplication::getStudentId, studentId)
                        .eq(StudentApplication::getType, TYPE_TRANSFER_MAJOR)
                        .eq(StudentApplication::getStatus, APPROVED));
                if (transferred != null && transferred > 0) {
                    throw new BizException("在校期间已有一次转专业经历，"
                            + "按学生手册第二十九条不能再申请");
                }
                notes.add("平均学分绩点 " + summary.gpa() + "，未低于 1.0 的门槛");
                notes.add("转专业受转入专业名额限制、绩点高者优先，最终以学院审批为准");
            }
            default -> notes.add("证明打印为事务性申请，教务处核对后出证");
        }
        return String.join("；", notes);
    }

    private boolean failedCourse(Long studentId, Long termId, Long courseId) {
        return mapper.failedCourses(studentId, termId).stream()
                .anyMatch(f -> Objects.equals(f.getCourseId(), courseId));
    }

    private boolean passedCourse(Long studentId, Long termId, Long courseId) {
        return mapper.passedCourses(studentId, termId).stream()
                .anyMatch(f -> Objects.equals(f.getCourseId(), courseId));
    }

    private static String weekdayText(Integer weekday) {
        String[] names = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return weekday != null && weekday >= 1 && weekday <= 7 ? names[weekday] : "时间待定";
    }

    /** 当前教学周。学期日期没配好时返回 null，不阻断流程。 */
    private Integer teachingWeek(Long termId) {
        Term term = termMapper.selectById(termId);
        if (term == null || term.getStartDate() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(term.getStartDate(), LocalDate.now());
        return days < 0 ? 0 : (int) (days / 7) + 1;
    }

    private List<ApplicationDtos.Row> toRows(List<StudentApplication> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> students = studentMapper.selectBatchIds(
                        list.stream().map(StudentApplication::getStudentId).distinct().toList()).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (x, y) -> x));
        Map<Long, Term> terms = termMapper.selectBatchIds(
                        list.stream().map(StudentApplication::getTermId).filter(Objects::nonNull)
                                .distinct().toList()).stream()
                .collect(Collectors.toMap(Term::getId, Function.identity(), (x, y) -> x));

        return list.stream()
                .sorted(Comparator.comparing(StudentApplication::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(a -> {
                    Student s = students.get(a.getStudentId());
                    Term t = terms.get(a.getTermId());
                    return new ApplicationDtos.Row(
                            a.getId(), a.getType(),
                            TYPE_TEXT.getOrDefault(a.getType(), a.getType()),
                            a.getStatus(),
                            s == null ? null : s.getStudentNo(),
                            s == null ? null : s.getName(),
                            a.getTermId(), t == null ? null : t.getName(),
                            a.getTarget(), a.getReason(), a.getMaterials(),
                            a.getRoomName(), a.getRoomWeekday(), a.getRoomStartSection(),
                            a.getRoomEndSection(), a.getRoomWeeks(),
                            a.getPrecheckNote(), a.getReviewer(), a.getReviewNote(),
                            fmt(a.getReviewedAt()), fmt(a.getCreatedAt()));
                })
                .toList();
    }

    private static String fmt(LocalDateTime time) {
        return time == null ? null : time.format(TS);
    }

    private static String num(Double d) {
        if (d == null) {
            return "—";
        }
        return d % 1 == 0 ? String.valueOf(d.intValue()) : String.valueOf(d);
    }
}
