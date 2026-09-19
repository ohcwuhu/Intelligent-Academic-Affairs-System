package com.iaas.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iaas.student.entity.Student;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}
