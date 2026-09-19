package com.iaas.student;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iaas.common.BizException;
import com.iaas.common.PageResult;
import com.iaas.enrollment.entity.Enrollment;
import com.iaas.enrollment.mapper.EnrollmentMapper;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import com.iaas.system.entity.Clazz;
import com.iaas.system.entity.College;
import com.iaas.system.entity.Major;
import com.iaas.system.mapper.ClazzMapper;
import com.iaas.system.mapper.CollegeMapper;
import com.iaas.system.mapper.MajorMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StudentService {

    private final StudentMapper studentMapper;
    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final ClazzMapper clazzMapper;
    private final EnrollmentMapper enrollmentMapper;

    public StudentService(StudentMapper studentMapper, CollegeMapper collegeMapper,
                          MajorMapper majorMapper, ClazzMapper clazzMapper,
                          EnrollmentMapper enrollmentMapper) {
        this.studentMapper = studentMapper;
        this.collegeMapper = collegeMapper;
        this.majorMapper = majorMapper;
        this.clazzMapper = clazzMapper;
        this.enrollmentMapper = enrollmentMapper;
    }

    /** 分页查询。keyword 匹配学号或姓名。 */
    public PageResult<StudentDtos.StudentVO> page(long pageNo, long pageSize, String keyword,
                                                  Long majorId, Long clazzId,
                                                  Integer grade, String status) {
        var query = Wrappers.<Student>lambdaQuery()
                .eq(majorId != null, Student::getMajorId, majorId)
                .eq(clazzId != null, Student::getClazzId, clazzId)
                .eq(grade != null, Student::getGrade, grade)
                .eq(status != null && !status.isBlank(), Student::getStatus, status)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(Student::getStudentNo, keyword).or().like(Student::getName, keyword))
                .orderByAsc(Student::getStudentNo);
        IPage<Student> p = studentMapper.selectPage(new Page<>(pageNo, pageSize), query);
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), toVO(p.getRecords()));
    }

    public StudentDtos.StudentVO get(Long id) {
        Student s = studentMapper.selectById(id);
        if (s == null) {
            throw BizException.notFound("学生");
        }
        return toVO(List.of(s)).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(StudentDtos.SaveRequest req) {
        validate(req);
        Student entity = new Student();
        entity.setId(req.id());
        entity.setStudentNo(req.studentNo());
        entity.setName(req.name());
        entity.setGender(req.gender() == null ? "男" : req.gender());
        entity.setBirthDate(req.birthDate());
        entity.setPhone(req.phone());
        entity.setEmail(req.email());
        entity.setCollegeId(req.collegeId());
        entity.setMajorId(req.majorId());
        entity.setClazzId(req.clazzId());
        entity.setGrade(req.grade());
        entity.setStatus(req.status() == null ? "在读" : req.status());

        if (req.id() == null) {
            Long dup = studentMapper.selectCount(Wrappers.<Student>lambdaQuery()
                    .eq(Student::getStudentNo, req.studentNo()));
            if (dup != null && dup > 0) {
                throw new BizException("学号已存在：" + req.studentNo());
            }
            studentMapper.insert(entity);
        } else {
            if (studentMapper.selectById(req.id()) == null) {
                throw BizException.notFound("学生");
            }
            studentMapper.updateById(entity);
        }
        return entity.getId();
    }

    /**
     * 删除学生。有选课记录时不允许直接删除，否则会产生孤儿数据；
     * 正确做法是把学籍状态改成「退学」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (studentMapper.selectById(id) == null) {
            return;
        }
        long enrolled = enrollmentMapper.selectCount(Wrappers.<Enrollment>lambdaQuery()
                .eq(Enrollment::getStudentId, id));
        if (enrolled > 0) {
            throw new BizException("该学生已有 " + enrolled + " 条选课记录，不能删除；"
                    + "可将学籍状态改为「退学」");
        }
        studentMapper.deleteById(id);
    }

    private void validate(StudentDtos.SaveRequest req) {
        if (req.studentNo() == null || req.studentNo().isBlank()) {
            throw new BizException("学号不能为空");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new BizException("姓名不能为空");
        }
        if (req.collegeId() == null || req.majorId() == null || req.clazzId() == null) {
            throw new BizException("学院、专业、班级均不能为空");
        }
        if (req.grade() == null) {
            throw new BizException("年级不能为空");
        }
    }

    private List<StudentDtos.StudentVO> toVO(List<Student> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, College> collegeMap = loadMap(collegeMapper.selectBatchIds(
                distinct(list, Student::getCollegeId)), College::getId);
        Map<Long, Major> majorMap = loadMap(majorMapper.selectBatchIds(
                distinct(list, Student::getMajorId)), Major::getId);
        Map<Long, Clazz> clazzMap = loadMap(clazzMapper.selectBatchIds(
                distinct(list, Student::getClazzId)), Clazz::getId);

        List<StudentDtos.StudentVO> result = new ArrayList<>();
        for (Student s : list) {
            College c = collegeMap.get(s.getCollegeId());
            Major m = majorMap.get(s.getMajorId());
            Clazz z = clazzMap.get(s.getClazzId());
            result.add(new StudentDtos.StudentVO(
                    s.getId(), s.getStudentNo(), s.getName(), s.getGender(), s.getBirthDate(),
                    s.getPhone(), s.getEmail(),
                    s.getCollegeId(), c == null ? null : c.getName(),
                    s.getMajorId(), m == null ? null : m.getName(),
                    s.getClazzId(), z == null ? null : z.getName(),
                    s.getGrade(), s.getStatus()));
        }
        return result;
    }

    private static List<Long> distinct(List<Student> list, Function<Student, Long> getter) {
        return list.stream().map(getter).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<Long, T> loadMap(List<T> list, Function<T, Long> idGetter) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(idGetter, Function.identity(), (x, y) -> x));
    }
}
