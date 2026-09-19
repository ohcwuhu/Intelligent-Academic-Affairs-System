package com.iaas.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iaas.common.BizException;
import com.iaas.common.PageResult;
import com.iaas.common.R;
import com.iaas.common.Roles;
import com.iaas.common.UserContext;
import com.iaas.system.entity.User;
import com.iaas.system.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** 账号管理（仅系统管理员）。 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @GetMapping
    public R<PageResult<User>> page(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size,
                                    @RequestParam(required = false) String role) {
        requireAdmin();
        var query = Wrappers.<User>lambdaQuery()
                .eq(role != null && !role.isBlank(), User::getRole, role)
                .orderByAsc(User::getId);
        IPage<User> result = userMapper.selectPage(new Page<>(page, size), query);
        // 口令散列不外传
        List<User> safe = result.getRecords().stream().peek(u -> u.setPassword(null)).toList();
        return R.ok(new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), safe));
    }

    /** 新建账号。口令以 BCrypt 存储，接口不回显明文。 */
    @PostMapping
    public R<Long> create(@RequestBody CreateUserRequest req) {
        requireAdmin();
        if (req.username() == null || req.username().isBlank()
                || req.password() == null || req.password().length() < 6) {
            throw new BizException("登录名不能为空，口令至少 6 位");
        }
        if (!List.of(Roles.ADMIN, Roles.ACADEMIC, Roles.TEACHER, Roles.STUDENT).contains(req.role())) {
            throw new BizException("角色不合法");
        }
        Long dup = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, req.username()));
        if (dup != null && dup > 0) {
            throw new BizException("登录名已存在：" + req.username());
        }
        User u = new User();
        u.setUsername(req.username());
        u.setPassword(encoder.encode(req.password()));
        u.setRealName(req.realName() == null ? req.username() : req.realName());
        u.setRole(req.role());
        u.setRefId(req.refId());
        u.setStatus(1);
        userMapper.insert(u);
        return R.ok(u.getId());
    }

    @PostMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest req) {
        UserContext.Principal me = UserContext.require();
        boolean self = me.userId().equals(id);
        if (!self && !Roles.ADMIN.equals(me.role())) {
            throw BizException.forbidden("只能修改本人密码");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new BizException("口令至少 6 位");
        }
        if (userMapper.selectById(id) == null) {
            throw BizException.notFound("账号");
        }
        User update = new User();
        update.setId(id);
        update.setPassword(encoder.encode(req.password()));
        userMapper.updateById(update);
        return R.ok();
    }

    @PostMapping("/{id}/status")
    public R<Void> setStatus(@PathVariable Long id, @RequestParam Integer status) {
        requireAdmin();
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态只能是 0（停用）或 1（启用）");
        }
        if (UserContext.require().userId().equals(id)) {
            throw new BizException("不能停用当前登录的账号");
        }
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
        return R.ok();
    }

    private void requireAdmin() {
        if (!Roles.ADMIN.equals(UserContext.require().role())) {
            throw BizException.forbidden("仅系统管理员可管理账号");
        }
    }

    public record CreateUserRequest(String username, String password, String realName,
                                    String role, Long refId) {
    }

    public record ResetPasswordRequest(String password) {
    }
}
