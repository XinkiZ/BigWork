package com.wylie.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 遗留课程目录访问配置（运行期可切换，用于演示中间层优化效果）。
 */
@Component
@ConfigurationProperties(prefix = "legacy")
public class LegacyProperties {

    /** 访问模式：direct=直连遗留库（慢）；cached=经中间层缓存（快） */
    private String mode = "cached";

    /** 遗留系统可用性开关（false 时模拟"课程目录不可用"） */
    private boolean available = true;

    /** 模拟遗留库访问延迟（毫秒） */
    private long simulatedLatencyMs = 8000;

    /** 中间层缓存有效期（秒） */
    private long cacheTtlSeconds = 600;

    /** 只读数据源 */
    private Db db = new Db();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public long getSimulatedLatencyMs() {
        return simulatedLatencyMs;
    }

    public void setSimulatedLatencyMs(long simulatedLatencyMs) {
        this.simulatedLatencyMs = simulatedLatencyMs;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public Db getDb() {
        return db;
    }

    public void setDb(Db db) {
        this.db = db;
    }

    public static class Db {
        private String url = "jdbc:mysql://localhost:3306/legacy_catalog?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true";
        private String username = "legacy_ro";
        private String password = "legacy_ro123";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}