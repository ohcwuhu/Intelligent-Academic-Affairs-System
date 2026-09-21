package com.iaas.textbook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iaas.textbook.entity.TextbookOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TextbookOrderMapper extends BaseMapper<TextbookOrder> {
}
