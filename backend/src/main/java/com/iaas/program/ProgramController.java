package com.iaas.program;

import com.iaas.common.BizException;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 培养方案与毕业审核。
 *
 * <p>学生只能审自己（身份取自令牌）；教务可以按学生查，用于答疑与毕审预演。
 */
@RestController
@RequestMapping("/api/program")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService service;

    @GetMapping
    public R<List<ProgramDtos.ProgramRow>> list() {
        UserContext.require();
        return R.ok(service.list());
    }

    @GetMapping("/{id}")
    public R<ProgramDtos.Detail> detail(@PathVariable Long id) {
        UserContext.require();
        return R.ok(service.detail(id));
    }

    /**
     * 毕业审核。学生不传 studentId（查自己），教务可指定学生。
     */
    @GetMapping("/audit")
    public R<ProgramDtos.Audit> audit(@RequestParam(required = false) Long studentId) {
        UserContext.Principal me = UserContext.require();
        if (me.isStudent()) {
            return R.ok(service.audit(me.requireStudentId()));
        }
        if (me.isStaff() && studentId != null) {
            return R.ok(service.audit(studentId));
        }
        throw BizException.forbidden("教师与教务请指定要审核的学生");
    }
}
