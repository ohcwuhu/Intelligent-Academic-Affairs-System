package com.iaas.workbench;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.application.ApplicationService;
import com.iaas.common.BizException;
import com.iaas.common.UserContext;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.enrollment.EnrollmentService;
import com.iaas.governance.GovernanceDtos;
import com.iaas.governance.GovernanceOverviewService;
import com.iaas.info.InfoDtos;
import com.iaas.info.InfoService;
import com.iaas.knowledge.KnowledgeGovernanceService;
import com.iaas.knowledge.KnowledgeChunkMapper;
import com.iaas.knowledge.KnowledgeDocumentMapper;
import com.iaas.knowledge.KnowledgeChunk;
import com.iaas.knowledge.KnowledgeDocument;
import com.iaas.program.mapper.ProgramMapper;
import com.iaas.student.mapper.StudentMapper;
import com.iaas.system.entity.Term;
import com.iaas.system.mapper.TermMapper;
import com.iaas.teaching.entity.TeachingClass;
import com.iaas.teaching.mapper.TeachingClassMapper;
import com.iaas.teacher.mapper.TeacherMapper;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 教务工作台。
 *
 * <p>教务的一天是"先看今天有什么要办的"，而不是"先去某个档案列表"。
 * 这些数字原本散在申请审批、反馈缺口、知识库、通知留言四个页面里，
 * 这里按"今天要处理什么"重新排一次。
 *
 * <p>本服务不自己算数：待办取自申请模块，问答质量取自治理概览，
 * 知识库取自知识库模块。口径只能有一处，否则工作台与各页面迟早对不上。
 */
@Service
@RequiredArgsConstructor
public class WorkbenchService {

    private final ApplicationService applicationService;
    private final InfoService infoService;
    private final GovernanceOverviewService overviewService;
    private final KnowledgeGovernanceService knowledgeGovernanceService;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final EnrollmentService enrollmentService;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CourseMapper courseMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ProgramMapper programMapper;
    private final TermMapper termMapper;

    public record Todo(String kind, Long id, String title, String detail, String createdAt) {
    }

    public record Workbench(
            long pendingApplications, long waitingMessages, long pendingFeedback, long pendingGaps,
            long askToday, long blockedToday, long injectionToday, double avgDurationMs,
            long effectiveDocuments, long chunks, long expiringDocuments,
            long students, long teachers, long courses, long teachingClasses, long programs,
            String termName,
            List<Todo> todos, List<InfoDtos.MessageRow> waitingList) {
    }

    public Workbench load() {
        if (!UserContext.require().isStaff()) {
            throw BizException.forbidden("工作台仅对教务与管理员开放");
        }
        Long termId = enrollmentService.currentTermId();
        Term term = termId == null ? null : termMapper.selectById(termId);

        var applications = applicationService.page(1, 5, "待审", null);
        List<Todo> todos = applications.records().stream()
                .map(r -> new Todo("APPLICATION", r.id(),
                        (r.studentName() == null ? "" : r.studentName() + " · ") + r.typeText(),
                        r.target() + "｜" + (r.precheckNote() == null ? "无预检结论" : r.precheckNote()),
                        r.createdAt()))
                .toList();

        List<InfoDtos.MessageRow> waiting = infoService.messages(null).stream()
                .filter(m -> m.reply() == null)
                .toList();

        GovernanceDtos.Overview overview = overviewService.overview();
        long effectiveDocs = documentMapper.selectCount(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getStatus, "生效"));
        long chunks = chunkMapper.selectCount(Wrappers.<KnowledgeChunk>lambdaQuery());
        long expiring = knowledgeGovernanceService.expiringSoon(90).size();
        long classes = termId == null ? 0
                : teachingClassMapper.selectCount(Wrappers.<TeachingClass>lambdaQuery()
                .eq(TeachingClass::getTermId, termId));

        return new Workbench(
                applications.total(), waiting.size(),
                overview.pendingFeedback(), overview.pendingGap(),
                overview.askToday(), overview.blockedToday(), overview.injectionToday(),
                overview.avgDurationMs(),
                effectiveDocs, chunks, expiring,
                studentMapper.selectCount(null), teacherMapper.selectCount(null),
                courseMapper.selectCount(null), classes, programMapper.selectCount(null),
                term == null ? null : term.getName(),
                todos, waiting.stream().limit(5).toList());
    }
}
