package com.iaas.export;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.UserContext;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.EnrollmentDtos;
import com.iaas.enrollment.EnrollmentService;
import com.iaas.fee.FeeService;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import com.iaas.student.StudentService;
import com.iaas.system.entity.Clazz;
import com.iaas.system.entity.College;
import com.iaas.system.entity.Major;
import com.iaas.system.entity.Term;
import com.iaas.system.mapper.ClazzMapper;
import com.iaas.system.mapper.CollegeMapper;
import com.iaas.system.mapper.MajorMapper;
import com.iaas.system.mapper.TermMapper;
import com.iaas.teaching.TeachingClassDtos;
import com.iaas.teaching.TeachingClassService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 导出。
 *
 * <p>教务每天都在交表：名单、成绩、账单都要出去。系统只进不出，线下就得手工抄一遍，
 * 所以导出的权限与页面权限一一对应——页面上能看什么，就能导出什么，不多给一行。
 *
 * <p>导的是"当前这一屏的数据"，不是全库：教师导出名单只导自己教学班的，
 * 学生导出成绩只导自己的。这跟页面上的可见范围完全一致，避免导出一个权限更大的副本。
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    public static final String TYPE_ROSTER = "roster";
    public static final String TYPE_STUDENTS = "students";
    public static final String TYPE_COURSES = "courses";
    public static final String TYPE_CLASSES = "teaching-classes";
    public static final String TYPE_MY_GRADES = "my-grades";
    public static final String TYPE_MY_FEE = "my-fee";

    private final EnrollmentService enrollmentService;
    private final TeachingClassService teachingClassService;
    private final StudentService studentService;
    private final FeeService feeService;
    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final ClazzMapper clazzMapper;
    private final TermMapper termMapper;

    public record CsvFile(String fileName, String content) {
    }

    public CsvFile export(String type, Long id, Long termId, String keyword) {
        UserContext.Principal me = UserContext.require();
        return switch (type) {
            case TYPE_ROSTER -> roster(me, id);
            case TYPE_STUDENTS -> students(me, keyword);
            case TYPE_COURSES -> courses(me, keyword);
            case TYPE_CLASSES -> classes(me, termId);
            case TYPE_MY_GRADES -> myGrades(me);
            case TYPE_MY_FEE -> myFee(me, termId);
            default -> throw new BizException("不支持导出这种数据：" + type);
        };
    }

    /** 教学班名单：教师限本人教学班（与名单页同一条边界）。 */
    private CsvFile roster(UserContext.Principal me, Long teachingClassId) {
        if (teachingClassId == null) {
            throw new BizException("请指定要导出的教学班");
        }
        if (me.isStudent()) {
            throw BizException.forbidden("学生无权导出教学班名单");
        }
        TeachingClassDtos.TeachingClassVO tc = teachingClassService.get(teachingClassId);
        if (me.isTeacher() && !java.util.Objects.equals(tc.teacherId(), me.requireTeacherId())) {
            throw BizException.forbidden("只能导出本人任教教学班的名单");
        }
        List<EnrollmentDtos.RosterItem> roster = enrollmentService.roster(teachingClassId);
        Csv csv = new Csv().header("学号", "姓名", "班级", "专业", "成绩", "绩点", "是否通过");
        for (EnrollmentDtos.RosterItem r : roster) {
            csv.row(r.studentNo(), r.studentName(), r.clazzName(), r.majorName(),
                    r.score(), r.gradePoint(), r.score() == null ? "未录入" : (r.passed() ? "通过" : "未通过"));
        }
        return new CsvFile(tc.code() + " 成绩单.csv", csv.build());
    }

    private CsvFile students(UserContext.Principal me, String keyword) {
        requireStaff(me);
        List<Student> list = studentMapper.selectList(Wrappers.<Student>lambdaQuery()
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(Student::getStudentNo, keyword).or().like(Student::getName, keyword))
                .orderByAsc(Student::getStudentNo));
        var colleges = mapOf(collegeMapper.selectList(null), College::getId, College::getName);
        var majors = mapOf(majorMapper.selectList(null), Major::getId, Major::getName);
        var clazzes = mapOf(clazzMapper.selectList(null), Clazz::getId, Clazz::getName);
        Csv csv = new Csv().header("学号", "姓名", "性别", "出生日期", "学院", "专业", "班级",
                "年级", "联系电话", "邮箱", "学籍状态");
        for (Student s : list) {
            csv.row(s.getStudentNo(), s.getName(), s.getGender(), s.getBirthDate(),
                    colleges.get(s.getCollegeId()), majors.get(s.getMajorId()),
                    clazzes.get(s.getClazzId()), s.getGrade(), s.getPhone(), s.getEmail(), s.getStatus());
        }
        return new CsvFile("学生档案.csv", csv.build());
    }

    private CsvFile courses(UserContext.Principal me, String keyword) {
        requireStaff(me);
        List<Course> list = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(Course::getCode, keyword).or().like(Course::getName, keyword))
                .orderByAsc(Course::getCode));
        var colleges = mapOf(collegeMapper.selectList(null), College::getId, College::getName);
        Csv csv = new Csv().header("课程代码", "课程名称", "学分", "学时", "课程性质", "考核方式", "开课学院");
        for (Course c : list) {
            csv.row(c.getCode(), c.getName(), c.getCredit(), c.getHours(),
                    c.getCourseType(), c.getAssessType(), colleges.get(c.getCollegeId()));
        }
        return new CsvFile("课程库.csv", csv.build());
    }

    private CsvFile classes(UserContext.Principal me, Long termId) {
        requireStaff(me);
        List<TeachingClassDtos.TeachingClassVO> list =
                teachingClassService.list(termId, null, null, false);
        Csv csv = new Csv().header("教学班代码", "课程代码", "课程名称", "任课教师",
                "学期", "上课时间", "教室", "容量", "已选", "剩余", "状态");
        for (TeachingClassDtos.TeachingClassVO c : list) {
            csv.row(c.code(), c.courseCode(), c.courseName(), c.teacherName(),
                    c.termName(), c.timeText(), c.classroom(),
                    c.capacity(), c.enrolled(), c.remaining(), c.status());
        }
        return new CsvFile("教学班开课.csv", csv.build());
    }

    private CsvFile myGrades(UserContext.Principal me) {
        Long studentId = me.requireStudentId();
        List<EnrollmentDtos.MyCourse> list = enrollmentService.myCourses(studentId, null);
        Csv csv = new Csv().header("学期", "课程代码", "课程名称", "学分", "成绩", "绩点", "状态");
        for (EnrollmentDtos.MyCourse c : list) {
            csv.row(c.termName(), c.courseCode(), c.courseName(), c.credit(),
                    c.score(), c.gradePoint(), c.scoreStatus());
        }
        return new CsvFile("我的成绩.csv", csv.build());
    }

    private CsvFile myFee(UserContext.Principal me, Long termId) {
        var bill = feeService.bill(me.requireStudentId(), termId);
        Csv csv = new Csv().header("学期", "课程代码", "课程名称", "学分", "收费项目",
                "单价(元/学分)", "金额(元)", "说明");
        for (var item : bill.items()) {
            csv.row(bill.termName(), item.courseCode(), item.courseName(), item.credit(),
                    item.item(), item.unitPrice(), item.amount(), item.reason());
        }
        csv.row("合计", "", "", "", "", "", bill.total(), "以教务处通知为准");
        return new CsvFile("我的学分收费.csv", csv.build());
    }

    private void requireStaff(UserContext.Principal me) {
        if (!me.isStaff()) {
            throw BizException.forbidden("仅教务人员可导出这份数据");
        }
    }

    private static <T> java.util.Map<Long, String> mapOf(List<T> list,
                                                        java.util.function.Function<T, Long> id,
                                                        java.util.function.Function<T, String> name) {
        java.util.Map<Long, String> map = new java.util.HashMap<>();
        for (T t : list) {
            map.put(id.apply(t), name.apply(t));
        }
        return map;
    }
}
