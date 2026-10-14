package org.lbl.system.log.support;

/**
 * 日志结果取值，对应 sys_login_log.result / sys_operation_log.result 两个 VARCHAR(16) 列。
 * 用常量而不是魔法字符串，是因为写入端（登录流程、操作日志切面）和查询端（列表筛选）都要用到，
 * 拼错一个字母就会让筛选条件永远查不到数据。
 */
public final class LogResult {
    /** 操作成功 / 登录成功。 */
    public static final String SUCCESS = "SUCCESS";
    /** 操作失败 / 用户名密码错误。 */
    public static final String FAILURE = "FAILURE";
    /** 因连续失败被锁定，仅登录日志会产生。 */
    public static final String LOCKED = "LOCKED";

    private LogResult() {
    }
}
