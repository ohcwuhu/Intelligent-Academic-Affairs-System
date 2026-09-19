package com.iaas.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.common.JwtUtil;
import com.iaas.common.UserContext;
import com.iaas.system.entity.User;
import com.iaas.system.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest req) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, req.username()));

        // 账号不存在与口令错误返回同一提示，避免账号枚举
        if (user == null || !encoder.matches(req.password(), user.getPassword())) {
            throw new BizException(401, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.forbidden("账号已被停用，请联系教务处");
        }

        UserContext.Principal principal = new UserContext.Principal(
                user.getId(), user.getUsername(), user.getRealName(), user.getRole(), user.getRefId());
        String token = jwtUtil.issue(principal);

        User touch = new User();
        touch.setId(user.getId());
        touch.setLastLogin(LocalDateTime.now());
        userMapper.updateById(touch);

        return new AuthDtos.LoginResponse(token, jwtUtil.ttlSeconds(),
                new AuthDtos.UserInfo(user.getId(), user.getUsername(),
                        user.getRealName(), user.getRole(), user.getRefId()));
    }

    /** 当前用户信息。姓名从库里实时读，避免令牌里的信息过期。 */
    public AuthDtos.UserInfo currentUser() {
        UserContext.Principal p = UserContext.require();
        User user = userMapper.selectById(p.userId());
        if (user == null) {
            throw new BizException(401, "账号不存在");
        }
        return new AuthDtos.UserInfo(user.getId(), user.getUsername(),
                user.getRealName(), user.getRole(), user.getRefId());
    }
}
