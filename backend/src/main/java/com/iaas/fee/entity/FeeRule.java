package com.iaas.fee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 收费规则：一个收费项目一个每学分单价。 */
@Data
@TableName("fee_rule")
public class FeeRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String item;
    private BigDecimal creditPrice;
    private String note;
    private LocalDate effectiveFrom;
    private Integer status;
    private LocalDateTime updatedAt;
}
