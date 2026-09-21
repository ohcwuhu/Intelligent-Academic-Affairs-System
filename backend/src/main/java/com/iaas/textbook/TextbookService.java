package com.iaas.textbook;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.UserContext;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.EnrollmentDtos;
import com.iaas.enrollment.EnrollmentService;
import com.iaas.teaching.entity.TeachingClass;
import com.iaas.teaching.mapper.TeachingClassMapper;
import com.iaas.textbook.entity.Textbook;
import com.iaas.textbook.entity.TextbookOrder;
import com.iaas.textbook.mapper.TextbookMapper;
import com.iaas.textbook.mapper.TextbookOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 教材订购。
 *
 * <p>做法与选课一致：教材挂在教学班上，学生看到的是"本学期我选的课用什么教材"，
 * 点一下订、再点一下取消。订购记录按"学生 × 教材"唯一，
 * 重复点不会多出一条——这类"点两下"的操作最容易把数据搞脏。
 */
@Service
@RequiredArgsConstructor
public class TextbookService {

    private final TextbookMapper textbookMapper;
    private final TextbookOrderMapper orderMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final CourseMapper courseMapper;
    private final EnrollmentService enrollmentService;

    /** 学生：本学期所选课程的教材清单 + 订购状态与金额小计。 */
    public TextbookDtos.MyTextbooks mine(Long termId) {
        Long studentId = UserContext.require().requireStudentId();
        Long term = termId != null ? termId : enrollmentService.currentTermId();
        List<EnrollmentDtos.MyCourse> courses = enrollmentService.myCourses(studentId, term);
        if (courses.isEmpty()) {
            return new TextbookDtos.MyTextbooks(List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        List<Long> classIds = courses.stream().map(EnrollmentDtos.MyCourse::teachingClassId).toList();
        List<Textbook> books = textbookMapper.selectList(Wrappers.<Textbook>lambdaQuery()
                .in(Textbook::getTeachingClassId, classIds)
                .orderByAsc(Textbook::getTeachingClassId));
        Set<Long> ordered = orderMapper.selectList(Wrappers.<TextbookOrder>lambdaQuery()
                        .eq(TextbookOrder::getStudentId, studentId)).stream()
                .filter(o -> !"已取消".equals(o.getStatus()))
                .map(TextbookOrder::getTextbookId).collect(Collectors.toSet());
        Map<Long, Course> courseByClass = courseMapOf(courses);

        List<TextbookDtos.StudentRow> rows = new ArrayList<>();
        BigDecimal orderedAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Textbook b : books) {
            Course c = courseByClass.get(b.getTeachingClassId());
            boolean isOrdered = ordered.contains(b.getId());
            BigDecimal price = b.getPrice() == null ? BigDecimal.ZERO : b.getPrice();
            totalAmount = totalAmount.add(price);
            if (isOrdered) {
                orderedAmount = orderedAmount.add(price);
            }
            rows.add(new TextbookDtos.StudentRow(
                    b.getId(), b.getTeachingClassId(),
                    codeOfCourse(courses, b.getTeachingClassId()),
                    c == null ? null : c.getName(), c == null ? null : c.getCode(),
                    b.getTitle(), b.getAuthor(), b.getPublisher(), b.getIsbn(), b.getPrice(),
                    b.getNote(), isOrdered));
        }
        return new TextbookDtos.MyTextbooks(rows, ordered.size(), orderedAmount, totalAmount);
    }

    @Transactional(rollbackFor = Exception.class)
    public void order(Long textbookId) {
        Long studentId = UserContext.require().requireStudentId();
        if (textbookMapper.selectById(textbookId) == null) {
            throw BizException.notFound("教材");
        }
        TextbookOrder existing = orderMapper.selectOne(Wrappers.<TextbookOrder>lambdaQuery()
                .eq(TextbookOrder::getTextbookId, textbookId)
                .eq(TextbookOrder::getStudentId, studentId));
        if (existing == null) {
            TextbookOrder o = new TextbookOrder();
            o.setTextbookId(textbookId);
            o.setStudentId(studentId);
            o.setStatus("已订购");
            o.setOrderedAt(java.time.LocalDateTime.now());
            orderMapper.insert(o);
        } else {
            existing.setStatus("已订购");
            existing.setOrderedAt(java.time.LocalDateTime.now());
            orderMapper.updateById(existing);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long textbookId) {
        Long studentId = UserContext.require().requireStudentId();
        TextbookOrder existing = orderMapper.selectOne(Wrappers.<TextbookOrder>lambdaQuery()
                .eq(TextbookOrder::getTextbookId, textbookId)
                .eq(TextbookOrder::getStudentId, studentId));
        if (existing == null) {
            return;
        }
        existing.setStatus("已取消");
        orderMapper.updateById(existing);
    }

    /** 教务：维护教材。 */
    @Transactional(rollbackFor = Exception.class)
    public Long save(TextbookDtos.SaveRequest req) {
        if (req.teachingClassId() == null || req.title() == null || req.title().isBlank()) {
            throw new BizException("请选择教学班并填写教材名称");
        }
        Textbook b = req.id() == null ? new Textbook() : textbookMapper.selectById(req.id());
        if (b == null) {
            throw BizException.notFound("教材");
        }
        b.setTeachingClassId(req.teachingClassId());
        b.setTitle(req.title().strip());
        b.setAuthor(req.author());
        b.setPublisher(req.publisher());
        b.setIsbn(req.isbn());
        b.setPrice(req.price());
        b.setNote(req.note());
        if (b.getId() == null) {
            textbookMapper.insert(b);
        } else {
            textbookMapper.updateById(b);
        }
        return b.getId();
    }

    /** 教务：教材清单与订购人数。 */
    public List<Map<String, Object>> listForStaff(Long teachingClassId) {
        var query = Wrappers.<Textbook>lambdaQuery().orderByAsc(Textbook::getTeachingClassId);
        if (teachingClassId != null) {
            query.eq(Textbook::getTeachingClassId, teachingClassId);
        }
        return textbookMapper.selectList(query).stream()
                .map(b -> {
                    long count = orderMapper.selectCount(Wrappers.<TextbookOrder>lambdaQuery()
                            .eq(TextbookOrder::getTextbookId, b.getId())
                            .ne(TextbookOrder::getStatus, "已取消"));
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", b.getId());
                    row.put("teachingClassId", b.getTeachingClassId());
                    row.put("teachingClassCode", codeOf(b.getTeachingClassId()));
                    row.put("title", b.getTitle());
                    row.put("publisher", b.getPublisher());
                    row.put("isbn", b.getIsbn());
                    row.put("price", b.getPrice());
                    row.put("orderedCount", count);
                    return row;
                })
                .toList();
    }

    private String codeOf(Long teachingClassId) {
        TeachingClass tc = teachingClassMapper.selectById(teachingClassId);
        return tc == null ? null : tc.getCode();
    }

    private Map<Long, Course> courseMapOf(List<EnrollmentDtos.MyCourse> courses) {
        List<Long> classIds = courses.stream().map(EnrollmentDtos.MyCourse::teachingClassId).toList();
        Map<Long, TeachingClass> classes = teachingClassMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(TeachingClass::getId, Function.identity(), (a, b) -> a));
        List<Long> courseIds = classes.values().stream().map(TeachingClass::getCourseId).distinct().toList();
        Map<Long, Course> courseById = courseIds.isEmpty() ? Map.of()
                : courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Course> byClass = new LinkedHashMap<>();
        for (TeachingClass tc : classes.values()) {
            Course c = courseById.get(tc.getCourseId());
            if (c != null) {
                byClass.put(tc.getId(), c);
            }
        }
        return byClass;
    }

    private static String codeOfCourse(List<EnrollmentDtos.MyCourse> courses, Long teachingClassId) {
        return courses.stream().filter(c -> Objects.equals(c.teachingClassId(), teachingClassId))
                .map(EnrollmentDtos.MyCourse::teachingClassCode).findFirst().orElse(null);
    }
}
