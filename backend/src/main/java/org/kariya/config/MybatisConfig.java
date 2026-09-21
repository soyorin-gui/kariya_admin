package org.kariya.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.*;

import java.time.LocalDateTime;

@Configuration
public class MybatisConfig {
    @Bean
    MybatisPlusInterceptor interceptor() {
        MybatisPlusInterceptor i = new MybatisPlusInterceptor();
        // PaginationInnerInterceptor is split into mybatis-plus-jsqlparser since MyBatis-Plus 3.5.9.
        i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return i;
    }

    @Bean
    MetaObjectHandler auditHandler() {
        return new MetaObjectHandler() {
            public void insertFill(MetaObject m) {
                strictInsertFill(m, "createdTime", LocalDateTime.class, LocalDateTime.now());
                strictInsertFill(m, "updatedTime", LocalDateTime.class, LocalDateTime.now());
            }

            public void updateFill(MetaObject m) {
                strictUpdateFill(m, "updatedTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
