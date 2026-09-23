package org.lbl.system.log.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.lbl.system.log.support.LogResult;
import org.lbl.system.log.service.OperationLogService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面：环绕被 {@link OperationLog} 标注的方法，记录操作人与耗时。
 * <p>
 * 之所以 order 设为 0（早于 Spring Security 的方法级权限拦截器）：
 * 让本切面成为最外层，权限被拒绝时也照样能记下一条 FAILURE——
 * "谁试图做了他没有权限做的事"是审计里最有价值的记录之一。
 * 如果切面在权限校验之内，这类尝试会被直接拦掉而完全不落库。
 * <p>
 * 因此这里用 finally 而不是 try/catch-return：无论成功、业务异常还是权限拒绝，都必须落一条日志。
 * 真正的写入由 {@link OperationLogService#record} 负责，它保证不抛异常，所以放在 finally 里是安全的。
 */
@Aspect
@Component
@Order(0)
public class OperationLogAspect {
    private final OperationLogService service;

    public OperationLogAspect(OperationLogService service) {
        this.service = service;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startedAt = System.nanoTime();
        String result = LogResult.SUCCESS;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            result = LogResult.FAILURE;
            throw ex;
        } finally {
            service.record(operationLog.module(), operationLog.action(), result, (System.nanoTime() - startedAt) / 1_000_000);
        }
    }
}
