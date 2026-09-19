package com.iaas.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import com.iaas.system.entity.Clazz;
import com.iaas.system.entity.College;
import com.iaas.system.entity.Major;
import com.iaas.system.entity.Term;
import com.iaas.system.mapper.ClazzMapper;
import com.iaas.system.mapper.CollegeMapper;
import com.iaas.system.mapper.MajorMapper;
import com.iaas.system.mapper.TermMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 基础数据：学院、专业、班级、学期。
 *
 * <p>查询对所有登录用户开放（前端下拉框需要），维护仅限教务侧。
 */
@RestController
@RequestMapping("/api/basic")
@RequiredArgsConstructor
public class BasicDataController {

    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final ClazzMapper clazzMapper;
    private final TermMapper termMapper;

    @GetMapping("/colleges")
    public R<List<College>> colleges() {
        UserContext.require();
        return R.ok(collegeMapper.selectList(
                Wrappers.<College>lambdaQuery().orderByAsc(College::getCode)));
    }

    @GetMapping("/majors")
    public R<List<Major>> majors(@RequestParam(required = false) Long collegeId) {
        UserContext.require();
        return R.ok(majorMapper.selectList(Wrappers.<Major>lambdaQuery()
                .eq(collegeId != null, Major::getCollegeId, collegeId)
                .orderByAsc(Major::getCode)));
    }

    @GetMapping("/clazzes")
    public R<List<Clazz>> clazzes(@RequestParam(required = false) Long majorId) {
        UserContext.require();
        return R.ok(clazzMapper.selectList(Wrappers.<Clazz>lambdaQuery()
                .eq(majorId != null, Clazz::getMajorId, majorId)
                .orderByAsc(Clazz::getCode)));
    }

    @GetMapping("/terms")
    public R<List<Term>> terms() {
        UserContext.require();
        return R.ok(termMapper.selectList(
                Wrappers.<Term>lambdaQuery().orderByDesc(Term::getCode)));
    }

    /** 当前学期。前端进页面时用于默认筛选，AI 侧用于判断"本学期"。 */
    @GetMapping("/current-term")
    public R<Term> currentTerm() {
        UserContext.require();
        return R.ok(termMapper.selectOne(
                Wrappers.<Term>lambdaQuery().eq(Term::getIsCurrent, 1).last("limit 1")));
    }

    @PostMapping("/college")
    public R<Long> saveCollege(@RequestBody College req) {
        requireStaff();
        if (req.getCode() == null || req.getName() == null) {
            throw new BizException("学院代码与名称不能为空");
        }
        if (req.getId() == null) {
            collegeMapper.insert(req);
        } else {
            collegeMapper.updateById(req);
        }
        return R.ok(req.getId());
    }

    @PostMapping("/major")
    public R<Long> saveMajor(@RequestBody Major req) {
        requireStaff();
        if (req.getCode() == null || req.getName() == null || req.getCollegeId() == null) {
            throw new BizException("专业代码、名称与所属学院不能为空");
        }
        if (req.getId() == null) {
            majorMapper.insert(req);
        } else {
            majorMapper.updateById(req);
        }
        return R.ok(req.getId());
    }

    @PostMapping("/clazz")
    public R<Long> saveClazz(@RequestBody Clazz req) {
        requireStaff();
        if (req.getCode() == null || req.getName() == null
                || req.getMajorId() == null || req.getGrade() == null) {
            throw new BizException("班级代码、名称、专业与年级不能为空");
        }
        if (req.getId() == null) {
            clazzMapper.insert(req);
        } else {
            clazzMapper.updateById(req);
        }
        return R.ok(req.getId());
    }

    /** 保存学期。设为当前学期时自动把其它学期置为非当前。 */
    @PostMapping("/term")
    public R<Long> saveTerm(@RequestBody Term req) {
        requireStaff();
        if (req.getCode() == null || req.getName() == null
                || req.getStartDate() == null || req.getEndDate() == null) {
            throw new BizException("学期代码、名称与起止日期不能为空");
        }
        if (req.getStartDate().isAfter(req.getEndDate())) {
            throw new BizException("开始日期不能晚于结束日期");
        }
        if (req.getId() == null) {
            termMapper.insert(req);
        } else {
            termMapper.updateById(req);
        }
        if (req.getIsCurrent() != null && req.getIsCurrent() == 1) {
            termMapper.update(null, Wrappers.<Term>lambdaUpdate()
                    .ne(Term::getId, req.getId())
                    .set(Term::getIsCurrent, 0));
        }
        return R.ok(req.getId());
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可维护基础数据");
        }
    }
}
