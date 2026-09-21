package org.kariya.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.kariya.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
