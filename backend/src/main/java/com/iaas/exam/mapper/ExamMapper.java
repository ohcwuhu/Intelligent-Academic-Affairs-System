package com.iaas.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iaas.exam.entity.Exam;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamMapper extends BaseMapper<Exam> {
}
