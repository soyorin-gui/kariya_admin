package org.lbl.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.lbl.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 字段名 → 中文标签，仅用于拼装校验失败提示。
     * 只列出会出现在请求体里的字段；查不到的字段名直接回显原名，不会导致 NPE。
     */
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("username", "用户名"),
            Map.entry("realName", "姓名"),
            Map.entry("phone", "手机号"),
            Map.entry("email", "邮箱"),
            Map.entry("deptId", "所属部门"),
            Map.entry("roleIds", "角色"),
            Map.entry("status", "状态"),
            Map.entry("oldPassword", "原密码"),
            Map.entry("newPassword", "新密码"),
            Map.entry("password", "密码"),
            Map.entry("roleName", "角色名称"),
            Map.entry("roleCode", "角色标识"),
            Map.entry("dataScope", "数据范围"),
            Map.entry("deptName", "部门名称"),
            Map.entry("deptCode", "部门编码"),
            Map.entry("leaderUserId", "负责人"),
            Map.entry("sortOrder", "排序"),
            Map.entry("menuName", "菜单名称"),
            Map.entry("menuType", "菜单类型"),
            Map.entry("routeName", "路由名称"),
            Map.entry("routePath", "路由地址"),
            Map.entry("component", "前端组件"),
            Map.entry("permissionCode", "权限标识"),
            Map.entry("icon", "图标"),
            Map.entry("visible", "可见性"),
            Map.entry("keepAlive", "页面缓存"),
            Map.entry("rememberMe", "记住我")
    );

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Result<Void> business(BusinessException ex) {
        log.warn("Business request rejected: {}", ex.getMessage());
        return Result.fail(400, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Result<Void> unauthorized(UnauthorizedException ex) {
        log.warn("Unauthenticated request rejected: {}", ex.getMessage());
        return Result.fail(401, ex.getMessage());
    }

    /**
     * 频率限制。必须与 400 分开：400 在前端语义里是"业务规则拒绝、改改参数再试"，
     * 而限流是"现在再试也没用"。用 429 前端才能给出"稍后再试"而不是"参数有误"的提示。
     */
    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    Result<Void> tooManyRequests(TooManyRequestsException ex) {
        log.warn("Request rate limited: {}", ex.getMessage());
        return Result.fail(429, ex.getMessage());
    }

    /**
     * 处理 @PreAuthorize 等权限校验抛出的拒绝异常。
     * <p>
     * 关键点：方法级权限校验是在 Controller 调用过程中抛异常的，此时请求已经进入 DispatcherServlet，
     * Spring Security 的 ExceptionTranslationFilter 在其外层，永远看不到这个异常；
     * 如果不在这里专门声明，它会被下面的 @ExceptionHandler(Exception.class) 兜住并返回 500
     * "系统繁忙，请稍后重试"——把"没有权限"错报成"服务端故障"。
     * AuthorizationDeniedException 继承自 AccessDeniedException，因此这一个 handler 同时覆盖两者。
     * 过滤器链层面的拒绝走 WebSecurityConfig 里的 accessDeniedHandler，两条路径最终都是 403。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Result<Void> forbidden(AccessDeniedException ex) {
        log.warn("Request denied by permission check: {}", ex.getMessage());
        return Result.fail(403, "没有访问该资源的权限");
    }

    /**
     * 唯一约束冲突的兜底。
     * <p>
     * 这类冲突正常情况下应该被业务校验提前拦住（例如新增用户前查重名），但并发提交、以及
     * "校验用的查询口径和唯一索引口径不一致"两种情况下仍可能漏到数据库层。
     * 如果不单独处理，它会落到下面的 Exception 兜底分支变成 500"系统繁忙"，
     * 让用户以为服务出故障了；实际只是数据重复，属于可以自行修正的业务错误。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Result<Void> duplicateKey(DuplicateKeyException ex) {
        log.warn("Unique constraint violated", ex);
        return Result.fail(400, "数据已存在：用户名、角色标识或部门编码等唯一字段重复");
    }

    /**
     * 参数校验失败。刻意把"哪个字段、错在哪"带回给前端。
     * <p>
     * 之前统一返回"参数校验失败"，看起来省事，实际体验很差：用户在手机号里多打了一位，
     * 得到的提示和"用户名太长"完全一样，只能自己一个个字段猜。而这个项目的其他地方
     * （重名校验、唯一约束兜底）都在刻意避免这种"看不出原因的失败"。
     * <p>
     * 只回显字段名与约束类型，不回显用户输入的值，也不回显内部类名/regex——
     * 后者会把后端校验规则的具体实现泄露出去（例如完整邮箱正则），没有必要。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Result<Void> validation(Exception ex) {
        log.warn("Request validation failed", ex);
        return Result.fail(400, describeFirstViolation(ex));
    }

    private String describeFirstViolation(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException invalid) {
            return invalid.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(error -> violationMessage(error.getField(), error.getCode()))
                    .orElse("参数校验失败");
        }
        if (ex instanceof ConstraintViolationException violations) {
            return violations.getConstraintViolations().stream()
                    .findFirst()
                    .map(violation -> violationMessage(lastNode(violation.getPropertyPath().toString()),
                            violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()))
                    .orElse("参数校验失败");
        }
        return "参数校验失败";
    }

    private String lastNode(String propertyPath) {
        int separator = propertyPath.lastIndexOf('.');
        return separator < 0 ? propertyPath : propertyPath.substring(separator + 1);
    }

    private String violationMessage(String field, String code) {
        String label = FIELD_LABELS.getOrDefault(field, field);
        return switch (code) {
            case "NotBlank", "NotNull", "NotEmpty" -> label + "不能为空";
            case "Size" -> label + "长度不合法";
            case "Pattern" -> label + "格式不正确";
            case "Email" -> label + "格式不正确";
            case "Min", "Max", "Positive", "PositiveOrZero", "Negative", "NegativeOrZero" -> label + "取值不合法";
            default -> label + "不合法";
        };
    }

    @ExceptionHandler(CannotCreateTransactionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Result<Void> databaseUnavailable(CannotCreateTransactionException ex) {
        log.error("Unable to open a JDBC transaction. Check datasource host, port, credentials, firewall, and MySQL availability.", ex);
        return Result.fail(503, "数据库连接不可用，请联系系统管理员");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    Result<Void> unknown(Exception ex) {
        log.error("Unhandled server error", ex);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}
