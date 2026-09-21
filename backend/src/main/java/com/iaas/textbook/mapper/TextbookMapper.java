package com.iaas.textbook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iaas.textbook.entity.Textbook;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TextbookMapper extends BaseMapper<Textbook> {
}
