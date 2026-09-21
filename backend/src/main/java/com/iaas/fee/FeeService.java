package com.iaas.fee;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.UserContext;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.EnrollmentDtos;
import com.iaas.enrollment.EnrollmentService;
import com.iaas.fee.entity.FeeRule;
import com.iaas.fee.mapper.FeeRuleMapper;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import com.iaas.system.entity.Term;
import com.iaas.system.mapper.TermMapper;
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

/**
 * 学分收费查询。
 *
 * <p>算钱这件事只做一步：<b>学分 × 单价</b>。单价由教务维护，
 * 学生看到的每一笔都写清"哪门课、多少学分、按哪个项目、单价多少"——
 * 收费是最不能含糊的地方，"合计 480 元"说不出所以然是不行的。
 *
 * <p>什么情况收费，判据来自手册第二十三条：
 * 重新修读要缴费；已通过的课再修（刷分）按重修口径；正常首修不收费。
 * 判断方式是拿学生本学期选的教学班去比对他自己以往的修读记录，不用额外维护一张收费台账。
 */
@Service
@RequiredArgsConstructor
public class FeeService {

    public static final String ITEM_RETAKE = "重新修读";
    public static final String ITEM_SCORE_RETRY = "刷分重新修读";

    private final FeeRuleMapper ruleMapper;
    private final StudentMapper studentMapper;
    private final TermMapper termMapper;
    private final CourseMapper courseMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final EnrollmentService enrollmentService;

    public List<FeeDtos.Rule> rules() {
        return ruleMapper.selectList(Wrappers.<FeeRule>lambdaQuery().orderByAsc(FeeRule::getItem))
                .stream().map(r -> new FeeDtos.Rule(r.getId(), r.getItem(), r.getCreditPrice(),
                        r.getNote(), r.getEffectiveFrom() == null ? null : r.getEffectiveFrom().toString(),
                        r.getStatus())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveRule(Long id, String item, BigDecimal creditPrice, String note) {
        if (item == null || item.isBlank() || creditPrice == null) {
            throw new BizException("收费项目与单价不能为空");
        }
        if (creditPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("单价不能是负数");
        }
        FeeRule rule = id == null ? new FeeRule() : ruleMapper.selectById(id);
        if (rule == null) {
            throw BizException.notFound("收费规则");
        }
        rule.setItem(item.trim());
        rule.setCreditPrice(creditPrice);
        rule.setNote(note);
        rule.setStatus(1);
        if (rule.getId() == null) {
            rule.setEffectiveFrom(java.time.LocalDate.now());
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
    }

    /** 学生账单：本学期选了哪些课要收费、为什么收、一共多少。 */
    public FeeDtos.Bill bill(Long studentId, Long termId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw BizException.notFound("学生");
        }
        Long term = termId != null ? termId : enrollmentService.currentTermId();
        Term termEntity = term == null ? null : termMapper.selectById(term);

        List<EnrollmentDtos.MyCourse> current = enrollmentService.myCourses(studentId, term);
        Map<String, FeeRule> rules = new LinkedHashMap<>();
        for (FeeRule r : ruleMapper.selectList(Wrappers.<FeeRule>lambdaQuery()
                .eq(FeeRule::getStatus, 1))) {
            rules.put(r.getItem(), r);
        }

        // 该生所有学期的选课，按课程分组：用来判"这门课是首修、重修还是刷分"
        Map<Long, List<EnrollmentDtos.MyCourse>> history = new LinkedHashMap<>();
        for (EnrollmentDtos.MyCourse c : enrollmentService.myCourses(studentId, null)) {
            Long courseId = courseIdOf(c.teachingClassId());
            if (courseId != null) {
                history.computeIfAbsent(courseId, k -> new ArrayList<>()).add(c);
            }
        }

        List<FeeDtos.BillItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (EnrollmentDtos.MyCourse c : current) {
            Long courseId = courseIdOf(c.teachingClassId());
            if (courseId == null) {
                continue;
            }
            List<EnrollmentDtos.MyCourse> all = history.getOrDefault(courseId, List.of());
            boolean hadFail = all.stream().anyMatch(x -> !Objects.equals(x.enrollmentId(), c.enrollmentId())
                    && x.score() != null && x.score().compareTo(new BigDecimal("60")) < 0);
            boolean hadPass = all.stream().anyMatch(x -> !Objects.equals(x.enrollmentId(), c.enrollmentId())
                    && x.score() != null && x.score().compareTo(new BigDecimal("60")) >= 0);
            String item = hadFail ? ITEM_RETAKE : hadPass ? ITEM_SCORE_RETRY : null;
            if (item == null) {
                continue; // 首修不收费
            }
            FeeRule rule = rules.get(item);
            if (rule == null) {
                continue;
            }
            BigDecimal amount = rule.getCreditPrice()
                    .multiply(c.credit() == null ? BigDecimal.ZERO : c.credit());
            total = total.add(amount);
            items.add(new FeeDtos.BillItem(c.courseCode(), c.courseName(),
                    c.credit(), item, rule.getCreditPrice(), amount,
                    hadFail ? "该课程以往未取得学分，本次属重新修读" : "该课程已通过，本次属刷分重新修读"));
        }

        List<String> notes = new ArrayList<>();
        notes.add("金额 = 课程学分 × 每学分单价；单价由教务处维护，可在「学分收费」页查看");
        notes.add("当前单价为演示数据：手册第二十三条只写「按规定缴交」，"
                + "并注明「收费标准按学院有关重新修读收费管理规定执行」，金额本身不在手册里");
        notes.add("最终应缴金额与缴费方式以教务处通知为准");
        return new FeeDtos.Bill(student.getId(), student.getStudentNo(), student.getName(),
                term, termEntity == null ? null : termEntity.getName(), items, total, notes);
    }

    private Long courseIdOf(Long teachingClassId) {
        if (teachingClassId == null) {
            return null;
        }
        TeachingClass tc = teachingClassMapper.selectById(teachingClassId);
        return tc == null ? null : tc.getCourseId();
    }

    /** 教务侧：按课程 ID 取课程，页面展示用（保留给后续扩展）。 */
    public Course course(Long courseId) {
        return courseMapper.selectById(courseId);
    }
}
