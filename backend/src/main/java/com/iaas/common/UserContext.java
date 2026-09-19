package com.iaas.common;

/**
 * 当前登录用户上下文（ThreadLocal）。
 *
 * <p>由 {@link AuthInterceptor} 在请求进入时写入、结束时清除，
 * 业务层通过 {@link #require()} 获取，避免在方法签名里层层传递。
 */
public final class UserContext {

    /**
     * @param refId 学生角色为学生 ID，教师角色为教师 ID，其余为 null。
     *              业务层一律用这个字段做数据范围判断，不信任前端传入的身份参数。
     */
    public record Principal(Long userId, String username, String realName, String role, Long refId) {

        public boolean isStudent() {
            return Roles.isStudent(role);
        }

        public boolean isTeacher() {
            return Roles.isTeacher(role);
        }

        public boolean isStaff() {
            return Roles.isStaff(role);
        }

        public Long requireStudentId() {
            if (!isStudent() || refId == null) {
                throw BizException.forbidden("当前账号不是学生身份");
            }
            return refId;
        }

        public Long requireTeacherId() {
            if (!isTeacher() || refId == null) {
                throw BizException.forbidden("当前账号不是教师身份");
            }
            return refId;
        }
    }

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Principal principal) {
        HOLDER.set(principal);
    }

    public static Principal get() {
        return HOLDER.get();
    }

    public static Principal require() {
        Principal p = HOLDER.get();
        if (p == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return p;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
