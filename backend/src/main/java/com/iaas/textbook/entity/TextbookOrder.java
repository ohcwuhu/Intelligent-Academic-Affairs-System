package com.iaas.textbook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 教材订购记录：学生 × 教材唯一。 */
@Data
@TableName("textbook_order")
public class TextbookOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long textbookId;
    private Long studentId;
    private String status;
    private LocalDateTime orderedAt;
}
