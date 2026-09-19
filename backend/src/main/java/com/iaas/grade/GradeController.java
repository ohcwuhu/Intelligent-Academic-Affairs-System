package com.iaas.grade;

import com.iaas.common.BizException;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import com.iaas.enrollment.EnrollmentDtos;
import com.iaas.enrollment.EnrollmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 成绩录入（教师侧）。
 *
 * <p>换算规则集中在 {@link GradePointCalculator}，本控制器只做权限与编排。
 */
@RestController
@RequestMapping("/api/grade")
@RequiredArgsConstructor
public class GradeController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/save")
    public R<Void> save(@RequestBody ScoreRequest req) {
        UserContext.Principal me = UserContext.require();
        if (req.enrollmentId() == null) {
            throw new BizException("缺少选课记录 ID");
        }
        if (me.isTeacher()) {
            enrollmentService.saveScoreAsTeacher(me.requireTeacherId(),
                    req.enrollmentId(), req.score(), req.scoreStatus());
        } else if (me.isStaff()) {
            enrollmentService.saveScore(req.enrollmentId(), req.score(), req.scoreStatus());
        } else {
            throw BizException.forbidden("学生无权录入成绩");
        }
        return R.ok();
    }

    /** 批量录入，用于整班提交。任一条不合法则整批回滚。 */
    @PostMapping("/batch")
    public R<Integer> batch(@RequestBody List<EnrollmentDtos.ScoreEntry> entries) {
        UserContext.Principal me = UserContext.require();
        if (me.isTeacher()) {
            return R.ok(enrollmentService.saveScoresAsTeacher(me.requireTeacherId(), entries));
        }
        if (me.isStaff()) {
            for (EnrollmentDtos.ScoreEntry e : entries) {
                enrollmentService.saveScore(e.enrollmentId(), e.score(), e.scoreStatus());
            }
            return R.ok(entries.size());
        }
        throw BizException.forbidden("学生无权录入成绩");
    }

    public record ScoreRequest(Long enrollmentId, BigDecimal score, String scoreStatus) {
    }
}
