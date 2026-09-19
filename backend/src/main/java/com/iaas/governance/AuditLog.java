package com.iaas.governance;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 审计日志。追加写，不提供修改与删除接口。 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;
    private Long userId;
    private String username;
    private String role;
    private String question;
    private String intent;
    private String mode;
    private Integer hitCount;
    private Integer citationCount;
    private Integer blocked;
    private String reason;
    private Integer durationMs;
    private String traceId;
    private LocalDateTime createdAt;
}
