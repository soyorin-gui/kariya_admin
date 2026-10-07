package org.lbl.system.log.risk.support;

/**
 * 当前操作日志尚无 action_code，先把稳定中文组合集中在一处，避免规则类各自散落魔法字符串。
 * 将来表中增加 action_code 时，只需替换这一层匹配口径。
 */
public final class AuditOperationActions {
    public static final String PERSONAL_CENTER = "个人中心";
    public static final String PASSWORD_CHANGE = "修改密码";
    public static final String USER_MANAGEMENT = "用户管理";
    public static final String PASSWORD_RESET = "重置密码";

    private AuditOperationActions() {
    }
}
