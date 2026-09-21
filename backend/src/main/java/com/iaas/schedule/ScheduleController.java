package com.iaas.schedule;

import com.iaas.common.BizException;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import com.iaas.enrollment.EnrollmentDtos;
import com.iaas.enrollment.EnrollmentService;
import com.iaas.teaching.TeachingClassDtos;
import com.iaas.teaching.TeachingClassService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 课表查询。
 *
 * <p>学生课表来自本人选课，教师课表来自本人任教教学班，
 * 二者都不接受前端指定他人身份。
 */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final EnrollmentService enrollmentService;
    private final TeachingClassService teachingClassService;
    private final ScheduleService scheduleService;
    /** 我的课表。termId 为空时取当前学期。 */
    @GetMapping("/my")
    public R<ScheduleDtos.Timetable> my(@RequestParam(required = false) Long termId) {
        UserContext.Principal me = UserContext.require();
        Long term = termId != null ? termId : enrollmentService.currentTermId();
        List<ScheduleDtos.TimetableEntry> entries = new ArrayList<>();

        if (me.isStudent()) {
            List<EnrollmentDtos.MyCourse> courses = enrollmentService.myCourses(me.refId(), term);
            for (EnrollmentDtos.MyCourse c : courses) {
                entries.add(new ScheduleDtos.TimetableEntry(
                        c.courseName(), c.courseCode(), c.teacherName(), null,
                        c.classroom(), c.weekday(), c.startSection(), c.endSection(),
                        c.timeText(), c.credit()));
            }
        } else if (me.isTeacher()) {
            List<TeachingClassDtos.TeachingClassVO> classes =
                    teachingClassService.list(term, null, me.refId(), false);
            for (TeachingClassDtos.TeachingClassVO tc : classes) {
                entries.add(new ScheduleDtos.TimetableEntry(
                        tc.courseName(), tc.courseCode(), null, tc.code(),
                        tc.classroom(), tc.weekday(), tc.startSection(), tc.endSection(),
                        tc.timeText(), tc.credit()));
            }
        } else {
            throw BizException.forbidden("教务人员请使用教学班列表查询课表");
        }
        return R.ok(new ScheduleDtos.Timetable(term, entries));
    }

    /**
     * 专业课表：某专业某年级该学期开出的课。
     *
     * <p>按教学班上的"面向专业/年级"筛，而不是按谁选了课反推——
     * 反推会把"别的专业来选修"的课也算进来，也会漏掉还没人选的开课。
     * 专业与年级留空表示看全校课表。
     */
    @GetMapping("/major")
    public R<ScheduleDtos.MajorTimetable> major(
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) Long termId) {
        UserContext.require();
        return R.ok(scheduleService.majorTimetable(majorId, grade, termId));
    }
}
