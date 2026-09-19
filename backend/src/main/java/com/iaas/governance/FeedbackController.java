package com.iaas.governance;

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

import java.util.List;

/**
 * 反馈接口。
 *
 * <p>用户侧只能提交与查看自己的；教务侧可以分页查看与处理。
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public R<Long> submit(@RequestBody GovernanceDtos.FeedbackRequest req) {
        UserContext.require();
        Feedback f = new Feedback();
        f.setQuestion(req.question());
        f.setType(req.type());
        f.setDetail(req.detail());
        f.setAnswerMode(req.answerMode());
        f.setAnswerDigest(req.answerDigest());
        f.setCitationPath(req.citationPath());
        return R.ok(feedbackService.submit(f));
    }

    @GetMapping("/mine")
    public R<List<Feedback>> mine() {
        return R.ok(feedbackService.mine());
    }

    @GetMapping
    public R<PageResult<Feedback>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String type) {
        return R.ok(feedbackService.page(page, size, status, type));
    }

    /** 已修正的反馈，评测脚本从这里拉取，纳入回归用例。 */
    @GetMapping("/corrected")
    public R<List<Feedback>> corrected(@RequestParam(defaultValue = "100") int limit) {
        return R.ok(feedbackService.corrected(limit));
    }

    @PostMapping("/{id}/handle")
    public R<Void> handle(@PathVariable Long id,
                          @RequestBody GovernanceDtos.HandleRequest req) {
        feedbackService.handle(id, req.status(), req.note());
        return R.ok();
    }
}
