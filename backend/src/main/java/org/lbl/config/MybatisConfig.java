package org.lbl.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.lbl.security.context.CurrentUser;
import org.springframework.context.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

@Configuration
public class MybatisConfig {
    @Bean
    MybatisPlusInterceptor interceptor() {
        MybatisPlusInterceptor i = new MybatisPlusInterceptor();
        // 从 MyBatis-Plus 3.5.9 开始，PaginationInnerInterceptor 被拆分到 mybatis-plus-jsqlparser 模块中。
        i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return i;
    }

    @Bean
    MetaObjectHandler auditHandler() {
        return new MetaObjectHandler() {
            public void insertFill(MetaObject m) {
                Long actorId = actorId();
                strictInsertFill(m, "createdBy", Long.class, actorId);
                strictInsertFill(m, "updatedBy", Long.class, actorId);
                strictInsertFill(m, "createdTime", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(m, "updatedTime", LocalDateTime.class, LocalDateTime.now());
            }

            public void updateFill(MetaObject m) {
                strictUpdateFill(m, "updatedBy", Long.class, actorId());
                strictUpdateFill(m, "updatedTime", LocalDateTime.class, LocalDateTime.now());
            }

            private Long actorId() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                return authentication != null && authentication.getPrincipal() instanceof CurrentUser current
                        ? current.id() : null;
            }
        };
    }
}
