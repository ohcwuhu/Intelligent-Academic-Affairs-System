package com.iaas.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iaas.course.entity.Course;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}
