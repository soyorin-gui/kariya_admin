package org.kariya.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.kariya.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Result<Void> validation(Exception ex) {
        log.warn("Request validation failed", ex);
        return Result.fail(400, "参数校验失败");
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
