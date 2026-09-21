package com.iaas.info;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.UserContext;
import com.iaas.info.entity.Notice;
import com.iaas.info.entity.StudentMessage;
import com.iaas.info.mapper.NoticeMapper;
import com.iaas.info.mapper.StudentMessageMapper;
import com.iaas.student.entity.Student;
import com.iaas.student.mapper.StudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通知与留言。
 *
 * <p>通知按角色过滤：发给全员的谁都能看，指定角色的只给那个角色看。
 * 留言是"学生问、教务答"：学生只能看自己的，教务看全部并回复。
 */
@Service
@RequiredArgsConstructor
public class InfoService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NoticeMapper noticeMapper;
    private final StudentMessageMapper messageMapper;
    private final StudentMapper studentMapper;

    // ------------------------------------------------------------------
    // 通知
    // ------------------------------------------------------------------

    public List<InfoDtos.NoticeRow> notices() {
        UserContext.Principal me = UserContext.require();
        return noticeMapper.selectList(Wrappers.<Notice>lambdaQuery()
                        .orderByDesc(Notice::getPinned)
                        .orderByDesc(Notice::getPublishedAt))
                .stream()
                // targetRole 为空 = 全员；否则只发给指定角色
                .filter(n -> n.getTargetRole() == null || n.getTargetRole().isBlank()
                        || n.getTargetRole().equals(me.role()))
                .map(n -> new InfoDtos.NoticeRow(n.getId(), n.getTitle(), n.getContent(),
                        n.getPublisher(), n.getTargetRole(),
                        n.getPinned() != null && n.getPinned() == 1,
                        n.getPublishedAt() == null ? null : n.getPublishedAt().format(TS)))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long publish(String title, String content, String targetRole, boolean pinned) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new BizException("通知标题与正文不能为空");
        }
        UserContext.Principal me = UserContext.require();
        Notice n = new Notice();
        n.setTitle(title.strip());
        n.setContent(content.strip());
        n.setPublisher(me.realName() == null ? me.username() : me.realName());
        n.setTargetRole(targetRole == null || targetRole.isBlank() ? null : targetRole);
        n.setPinned(pinned ? 1 : 0);
        n.setPublishedAt(LocalDateTime.now());
        noticeMapper.insert(n);
        return n.getId();
    }

    // ------------------------------------------------------------------
    // 留言
    // ------------------------------------------------------------------

    public List<InfoDtos.MessageRow> messages(Long studentId) {
        UserContext.Principal me = UserContext.require();
        Long scope = me.isStudent() ? me.requireStudentId() : studentId;
        var query = Wrappers.<StudentMessage>lambdaQuery()
                .eq(scope != null, StudentMessage::getStudentId, scope)
                .orderByDesc(StudentMessage::getCreatedAt);
        List<StudentMessage> list = messageMapper.selectList(query);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> students = studentMapper.selectBatchIds(
                        list.stream().map(StudentMessage::getStudentId).distinct().toList()).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        return list.stream().map(m -> {
            Student s = students.get(m.getStudentId());
            return new InfoDtos.MessageRow(m.getId(),
                    s == null ? null : s.getStudentNo(), s == null ? null : s.getName(),
                    m.getContent(), m.getReply(), m.getRepliedBy(),
                    m.getRepliedAt() == null ? null : m.getRepliedAt().format(TS),
                    m.getCreatedAt() == null ? null : m.getCreatedAt().format(TS));
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long ask(String content) {
        if (content == null || content.isBlank()) {
            throw new BizException("留言内容不能为空");
        }
        StudentMessage m = new StudentMessage();
        m.setStudentId(UserContext.require().requireStudentId());
        m.setContent(content.strip());
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
        return m.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void reply(Long id, String reply) {
        if (reply == null || reply.isBlank()) {
            throw new BizException("回复内容不能为空");
        }
        StudentMessage m = messageMapper.selectById(id);
        if (m == null) {
            throw BizException.notFound("留言");
        }
        UserContext.Principal me = UserContext.require();
        m.setReply(reply.strip());
        m.setRepliedBy(me.realName() == null ? me.username() : me.realName());
        m.setRepliedAt(LocalDateTime.now());
        messageMapper.updateById(m);
    }

    /** 教务首页用：待回复的留言数。 */
    public long pendingMessages() {
        return messageMapper.selectCount(Wrappers.<StudentMessage>lambdaQuery()
                .isNull(StudentMessage::getReply));
    }

    static boolean same(Long a, Long b) {
        return Objects.equals(a, b);
    }
}
