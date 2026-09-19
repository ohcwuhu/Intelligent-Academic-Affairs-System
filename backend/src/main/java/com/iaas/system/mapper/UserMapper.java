package com.iaas.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iaas.system.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
