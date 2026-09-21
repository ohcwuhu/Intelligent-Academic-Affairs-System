package com.iaas.fee;

import com.iaas.common.BizException;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学分收费查询。
 *
 * <p>学生只看自己的账单（身份取自令牌）；单价维护与查他人账单只对教务开放。
 */
@RestController
@RequestMapping("/api/fee")
@RequiredArgsConstructor
public class FeeController {

    private final FeeService service;

    /** 收费项目与单价：登录即可查看，学生也能知道自己按什么标准交。 */
    @GetMapping("/rules")
    public R<List<FeeDtos.Rule>> rules() {
        UserContext.require();
        return R.ok(service.rules());
    }

    @PostMapping("/rule")
    public R<Void> saveRule(@RequestBody RuleRequest req) {
        requireStaff();
        service.saveRule(req.id(), req.item(), req.creditPrice(), req.note());
        return R.ok();
    }

    @GetMapping("/bill")
    public R<FeeDtos.Bill> bill(@RequestParam(required = false) Long termId,
                                @RequestParam(required = false) Long studentId) {
        UserContext.Principal me = UserContext.require();
        if (me.isStudent()) {
            return R.ok(service.bill(me.requireStudentId(), termId));
        }
        if (me.isStaff() && studentId != null) {
            return R.ok(service.bill(studentId, termId));
        }
        throw BizException.forbidden("教师与教务请指定要查询的学生");
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可维护收费标准");
        }
    }

    public record RuleRequest(Long id, String item, BigDecimal creditPrice, String note) {
    }
}
