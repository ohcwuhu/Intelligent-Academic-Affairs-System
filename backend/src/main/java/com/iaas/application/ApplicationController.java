package com.iaas.application;

import com.iaas.common.BizException;
import com.iaas.common.PageResult;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 办事与审批接口。
 *
 * <p>权限按动作分：申请类只给本人（学生身份取自令牌，不接前端传入的 studentId），
 * 审批类只给教务与管理员。教师既不能提交也不能审批，他的事在成绩录入那条线上。
 */
@RestController
@RequestMapping("/api/application")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService service;

    /** 申请类型清单，供前端渲染下拉项。 */
    @GetMapping("/types")
    public R<List<String>> types() {
        UserContext.require();
        return R.ok(service.types());
    }

    /** 某类申请的可选对象。 */
    @GetMapping("/options")
    public R<List<ApplicationDtos.Option>> options(@RequestParam String type) {
        return R.ok(service.options(type));
    }

    @PostMapping
    public R<ApplicationDtos.SubmitResult> submit(@RequestBody ApplicationDtos.SubmitRequest req) {
        UserContext.require().requireStudentId();
        return R.ok(service.submit(req));
    }

    @GetMapping("/mine")
    public R<List<ApplicationDtos.Row>> mine() {
        requireStudent();
        return R.ok(service.mine());
    }

    @PostMapping("/{id}/withdraw")
    public R<Void> withdraw(@PathVariable Long id) {
        requireStudent();
        service.withdraw(id);
        return R.ok();
    }

    /** 出具证明：只有已通过的证明打印申请能出，学生只能出自己的。 */
    @GetMapping("/{id}/certificate")
    public R<ApplicationDtos.Certificate> certificate(@PathVariable Long id) {
        return R.ok(service.certificate(id));
    }

    /** 审批列表：教务与管理员可见。 */
    @GetMapping
    public R<PageResult<ApplicationDtos.Row>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        requireStaff();
        return R.ok(service.page(page, size, status, type));
    }

    @PostMapping("/{id}/review")
    public R<ApplicationDtos.Row> review(@PathVariable Long id,
                                         @RequestBody ApplicationDtos.ReviewRequest req) {
        requireStaff();
        return R.ok(service.review(id, req));
    }

    private void requireStudent() {
        if (!UserContext.require().isStudent()) {
            throw BizException.forbidden("只有学生本人可以查看自己的申请");
        }
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可审批申请");
        }
    }
}
