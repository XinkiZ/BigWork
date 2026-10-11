package com.wylie.registration.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 主业务库数据源（bigwork）。
 * 由于系统中存在第二个数据源（遗留课程目录库），此处显式声明主数据源并标记 @Primary，
 * 保证 JPA / 事务管理器始终绑定主业务库。
 */
@Configuration
public class DataSourceConfig {

    @Bean(name = "dataSource")
    @Primary
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}