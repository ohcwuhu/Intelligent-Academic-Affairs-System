package com.iaas.common;

/** 角色常量。数据范围与角色的对应见 PRD 需求 REQ-BASE-01。 */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String ACADEMIC = "ACADEMIC";
    public static final String TEACHER = "TEACHER";
    public static final String STUDENT = "STUDENT";

    private Roles() {
    }

    /** 教务侧角色：管理员与教务管理员。 */
    public static boolean isStaff(String role) {
        return ADMIN.equals(role) || ACADEMIC.equals(role);
    }

    public static boolean isStudent(String role) {
        return STUDENT.equals(role);
    }

    public static boolean isTeacher(String role) {
        return TEACHER.equals(role);
    }
}
