package com.iaas.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iaas.enrollment.entity.Enrollment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EnrollmentMapper extends BaseMapper<Enrollment> {
}
