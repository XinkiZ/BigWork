package com.wylie.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 学费计算配置 */
@Component
@ConfigurationProperties(prefix = "tuition")
public class TuitionProperties {

    /** 每学分学费（元） */
    private BigDecimal perCredit = new BigDecimal("500");

    public BigDecimal getPerCredit() {
        return perCredit;
    }

    public void setPerCredit(BigDecimal perCredit) {
        this.perCredit = perCredit;
    }
}