package com.iaas.program.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 培养方案。毕业审核的基准：够不够毕业，看的是它，不是感觉。
 *
 * <p>同一专业可能有多份（不同年级培养方案不一样），所以用 status 标"现行"，
 * 审核时取现行的那一份；导入新方案会把旧方案置为停用，而不是删掉——
 * 往届生还得按当年的方案审。
 */
@Data
@TableName("program")
public class Program {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long majorId;
    private String majorName;
    private Integer grade;
    private String title;
    private String degree;
    private String duration;
    private BigDecimal minCredit;
    private String sourceNote;
    private String status;
    private LocalDateTime importedAt;
}
