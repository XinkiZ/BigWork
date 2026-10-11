package com.wylie.registration.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 遗留课程目录库（只读）数据源。
 * 使用独立的只读账号（legacy_ro，仅 SELECT 权限），从物理上保证新系统只读遗留数据。
 */
@Configuration
public class LegacyDataSourceConfig {

    @Bean(name = "legacyDataSource", destroyMethod = "close")
    public HikariDataSource legacyDataSource(LegacyProperties props) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(props.getDb().getUrl());
        ds.setUsername(props.getDb().getUsername());
        ds.setPassword(props.getDb().getPassword());
        ds.setPoolName("legacy-catalog-pool");
        ds.setMaximumPoolSize(5);
        ds.setReadOnly(true);
        return ds;
    }

    @Bean(name = "legacyJdbcTemplate")
    public JdbcTemplate legacyJdbcTemplate(@Qualifier("legacyDataSource") HikariDataSource legacyDataSource) {
        return new JdbcTemplate(legacyDataSource);
    }
}