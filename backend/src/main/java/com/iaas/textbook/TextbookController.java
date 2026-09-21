package com.iaas.textbook;

import com.iaas.common.BizException;
import com.iaas.common.R;
import com.iaas.common.UserContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 教材订购。
 *
 * <p>学生只能操作自己的订购记录；教材维护与订购统计只给教务。
 */
@RestController
@RequestMapping("/api/textbook")
@RequiredArgsConstructor
public class TextbookController {

    private final TextbookService service;

    @GetMapping("/my")
    public R<TextbookDtos.MyTextbooks> mine(@RequestParam(required = false) Long termId) {
        UserContext.require().requireStudentId();
        return R.ok(service.mine(termId));
    }

    @PostMapping("/{id}/order")
    public R<Void> order(@PathVariable Long id) {
        UserContext.require().requireStudentId();
        service.order(id);
        return R.ok();
    }

    @DeleteMapping("/{id}/order")
    public R<Void> cancel(@PathVariable Long id) {
        UserContext.require().requireStudentId();
        service.cancel(id);
        return R.ok();
    }

    @GetMapping
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) Long teachingClassId) {
        requireStaff();
        return R.ok(service.listForStaff(teachingClassId));
    }

    @PostMapping
    public R<Long> save(@RequestBody TextbookDtos.SaveRequest req) {
        requireStaff();
        return R.ok(service.save(req));
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可维护教材");
        }
    }
}
