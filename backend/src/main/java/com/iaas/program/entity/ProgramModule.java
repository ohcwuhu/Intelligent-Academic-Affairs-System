package com.iaas.program.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 培养方案的学分结构：某个模块要求多少学分。 */
@Data
@TableName("program_module")
public class ProgramModule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long programId;
    private String category;
    private String hoursText;
    private BigDecimal credit;
    private BigDecimal ratio;
    private Integer sortNo;
}
