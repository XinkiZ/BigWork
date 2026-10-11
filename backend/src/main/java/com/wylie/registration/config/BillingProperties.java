package com.wylie.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 计费系统（外部）模拟配置 */
@Component
@ConfigurationProperties(prefix = "billing")
public class BillingProperties {

    /** 计费系统可用性开关（false 时交易失败并按周期重试） */
    private boolean available = true;

    /** 失败重试周期（秒） */
    private long retryIntervalSeconds = 30;

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public long getRetryIntervalSeconds() {
        return retryIntervalSeconds;
    }

    public void setRetryIntervalSeconds(long retryIntervalSeconds) {
        this.retryIntervalSeconds = retryIntervalSeconds;
    }
}