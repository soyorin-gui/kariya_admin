package org.lbl.system.log.aspect;

import java.lang.annotation.*;

/**
 * 标记需要写入操作日志的方法。
 * <p>
 * 刻意做成"显式标注"而不是"记录所有请求"：审计表的增长速度完全取决于记什么。
 * 如果把全部 GET 查询都记下来，日志量会比业务数据高两三个数量级（详见 LogRetentionJob 的说明），
 * 所以默认只记增删改和导出这类"改变了什么"或"拿走了什么"的动作。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    /** 业务模块，例如"用户管理"。 */
    String module();

    /** 具体动作，例如"新增用户"。 */
    String action();
}
