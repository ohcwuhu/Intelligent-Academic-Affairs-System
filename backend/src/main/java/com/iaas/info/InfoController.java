package com.iaas.info;

import com.iaas.common.BizException;
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
 * 通知与留言。
 *
 * <p>通知人人可看（按角色过滤），发布只给教务；
 * 留言学生只能看自己的，教务看全部并回复。
 */
@RestController
@RequestMapping("/api/info")
@RequiredArgsConstructor
public class InfoController {

    private final InfoService service;

    @GetMapping("/notices")
    public R<List<InfoDtos.NoticeRow>> notices() {
        return R.ok(service.notices());
    }

    @PostMapping("/notice")
    public R<Long> publish(@RequestBody NoticeRequest req) {
        requireStaff();
        return R.ok(service.publish(req.title(), req.content(), req.targetRole(),
                Boolean.TRUE.equals(req.pinned())));
    }

    @GetMapping("/messages")
    public R<List<InfoDtos.MessageRow>> messages(@RequestParam(required = false) Long studentId) {
        UserContext.Principal me = UserContext.require();
        if (me.isTeacher()) {
            throw BizException.forbidden("教师请通过教学班名单与学生沟通");
        }
        return R.ok(service.messages(studentId));
    }

    @PostMapping("/message")
    public R<Long> ask(@RequestBody AskRequest req) {
        UserContext.require().requireStudentId();
        return R.ok(service.ask(req.content()));
    }

    @PostMapping("/message/{id}/reply")
    public R<Void> reply(@PathVariable Long id, @RequestBody AskRequest req) {
        requireStaff();
        service.reply(id, req.content());
        return R.ok();
    }

    private void requireStaff() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("仅教务人员可发布通知与回复留言");
        }
    }

    public record NoticeRequest(String title, String content, String targetRole, Boolean pinned) {
    }

    public record AskRequest(String content) {
    }
}
