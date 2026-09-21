package com.iaas.textbook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 教材：挂在教学班上（同一门课不同老师可能用不同教材）。 */
@Data
@TableName("textbook")
public class Textbook {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teachingClassId;
    private String title;
    private String author;
    private String publisher;
    private String isbn;
    private BigDecimal price;
    private String note;
}
