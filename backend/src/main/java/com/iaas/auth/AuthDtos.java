package com.iaas.auth;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "不能为空") String username,
            @NotBlank(message = "不能为空") String password) {
    }

    /**
     * @param refId 学生角色为学生 ID，教师角色为教师 ID，其余为 null。
     *              前端据此决定进入学生端还是管理端。
     */
    public record LoginResponse(String token, long expiresIn, UserInfo user) {
    }

    public record UserInfo(Long id, String username, String realName, String role, Long refId) {
    }
}
