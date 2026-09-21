package com.iaas.info.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 通知：教务发布，按角色可见。 */
@Data
@TableName("notice")
public class Notice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String publisher;
    /** 空表示发给所有人 */
    private String targetRole;
    private Integer pinned;
    private LocalDateTime publishedAt;
}
