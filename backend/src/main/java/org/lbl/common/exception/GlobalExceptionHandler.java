package org.lbl.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.lbl.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.lang.reflect.RecordComponent;
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
            Map.entry("confirmPassword", "确认密码"),
            Map.entry("captchaId", "验证码标识"),
            Map.entry("captchaCode", "验证码"),
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    Result<Void> uploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Upload rejected because it exceeds the multipart limit");
        return Result.fail(413, "文件过大，请压缩或拆分后重试");
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
        return Result.fail(400, "数据已存在：用户名、角色标识、部门编码、菜单路由或权限标识等唯一字段重复");
    }

    /**
     * 参数校验失败。刻意把"哪个字段、错在哪"带回给前端。
     * <p>
     * 之前统一返回"参数校验失败"，看起来省事，实际体验很差：用户在手机号里多打了一位，
     * 得到的提示和"用户名太长"完全一样，只能自己一个个字段猜。而这个项目的其他地方
     * （重名校验、唯一约束兜底）都在刻意避免这种"看不出原因的失败"。
     * <p>
     * 提示语的优先级见 {@code authoredOrFallback}：先用记录上<b>手写</b>的那句，
     * 没有才退回"字段标签 + 约束类型"。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Result<Void> validation(Exception ex) {
        log.warn("Request validation failed", ex);
        return Result.fail(400, describeFirstViolation(ex));
    }

    private String describeFirstViolation(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException invalid) {
            BindingResult binding = invalid.getBindingResult();
            return binding.getFieldErrors().stream()
                    .findFirst()
                    .map(error -> authoredOrFallback(error, binding))
                    .orElse("参数校验失败");
        }
        if (ex instanceof ConstraintViolationException violations) {
            return violations.getConstraintViolations().stream()
                    .findFirst()
                    .map(this::authoredOrFallback)
                    .orElse("参数校验失败");
        }
        return "参数校验失败";
    }

    /**
     * 决定给用户看哪一句：<b>优先用注解上手写的 message</b>。
     * <p>
     * 手写文案的信息量远大于"字段 + 约束类型"的机械拼装。密码规则就是最典型的例子：
     * 手写的是 {@link org.lbl.auth.service.PasswordRules#MESSAGE} 这类完整、可直接展示的业务规则，
     * 拼装出来的只有「密码格式不正确」—— 前者能让人改对，后者等于没说。
     * <p>
     * <b>但绝不能无条件采用框架解析出来的那句话。</b>Bean Validation 的内置提示会
     * <b>按 JVM 默认语言本地化</b>，而 {@code @Pattern} 的内置提示会把正则插值进去：
     * 在中文环境下它是「需要匹配 ^(?=.*[a-z])…」，把整条密码正则原样交给调用方。
     * 所以不能靠"是不是中文/英文"来区分（本项目正是在中文环境下跑的），
     * 必须<b>直接判断注解有没有写 message</b>：
     * <ul>
     *   <li>{@link ConstraintViolationException} 一侧有现成的 {@code getMessageTemplate()}，
     *       没写 message 时它以 {@code {} 开头（形如 {@code {jakarta.validation.constraints.Pattern.message}}）；</li>
     *   <li>{@link MethodArgumentNotValidException} 一侧 Spring 不暴露模板，
     *       改为从被校验对象上把注解反射出来读它的 {@code message} 属性。</li>
     * </ul>
     * 两条路都判断不出来时退回 {@link #violationMessage}，也就是改动前的行为 ——
     * 失败方向是安全的（宁可少给信息，也不把正则漏出去）。
     */
    private String authoredOrFallback(FieldError error, BindingResult binding) {
        String authored = declaredMessage(binding.getTarget(), error.getField(), error.getCode());
        // getDefaultMessage 是已经插值过的最终文案；只在确认注解写了 message 之后才用它。
        return authored != null ? authored : violationMessage(error.getField(), error.getCode());
    }

    private String authoredOrFallback(ConstraintViolation<?> violation) {
        String template = violation.getMessageTemplate();
        // 没写 message 时，模板是 "{jakarta.validation.constraints.X.message}" 这种包引用。
        if (template == null || template.isBlank() || template.startsWith("{")) {
            return violationMessage(lastNode(violation.getPropertyPath().toString()),
                    violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
        }
        return violation.getMessage();
    }

    /**
     * 反射读出被校验对象上某个字段的、指定约束注解所声明的 message。
     *
     * @return 手写的 message；注解没写、或该字段/注解找不到时返回 {@code null}（调用方退回拼装提示）
     */
    private static String declaredMessage(Object target, String field, String constraint) {
        if (target == null || field == null || constraint == null) return null;
        try {
            Class<?> type = target.getClass();
            // 本项目的请求体都是 record，注解会同时传播到访问器与字段上，任一处能读到即可。
            for (RecordComponent component : type.getRecordComponents()) {
                if (!component.getName().equals(field)) continue;
                String declared = declaredMessage(component.getAccessor().getAnnotations(), constraint);
                if (declared != null) return declared;
                break;
            }
            return declaredMessage(type.getDeclaredField(field).getAnnotations(), constraint);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            // 嵌套路径（如 items[0].name）、非 record 目标、字段名对不上 —— 一律退回拼装提示。
            return null;
        }
    }

    private static String declaredMessage(java.lang.annotation.Annotation[] annotations, String constraint) {
        for (java.lang.annotation.Annotation annotation : annotations) {
            if (!annotation.annotationType().getSimpleName().equals(constraint)) continue;
            try {
                Object value = annotation.annotationType().getMethod("message").invoke(annotation);
                // 以 "{" 开头说明是引用消息包的模板（等于没写手写文案）。
                if (value instanceof String text && !text.isBlank() && !text.startsWith("{")) return text;
            } catch (ReflectiveOperationException ex) {
                return null;
            }
        }
        return null;
    }

    private String lastNode(String propertyPath) {
        int separator = propertyPath.lastIndexOf('.');
        return separator < 0 ? propertyPath : propertyPath.substring(separator + 1);
    }

    /**
     * 兜底提示：只在拿不到手写 message 时使用（见 {@code authoredOrFallback}）。
     * <p>
     * 因此 {@link #FIELD_LABELS} 缺项的表现是<b>字段的 Java 名直接出现在中文界面里</b>
     * （例如「captchaCode 长度不合法」而不是「验证码长度不合法」）。
     * 目前全项目的校验注解里只有极少数没有手写 message，这条路很少会走到。
     */
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
