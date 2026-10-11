package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.config.BillingProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 外部计费系统（Billing System）模拟客户端。
 * 通过开关模拟计费系统宕机，用于演示"按固定周期重试发送，直到系统恢复可用"。
 */
@Component
public class BillingSystemClient {

    private final BillingProperties props;

    public BillingSystemClient(BillingProperties props) {
        this.props = props;
    }

    /** 向计费系统发送一笔交易，计费系统不可用时抛出异常 */
    public void send(String studentNo, String semesterName, java.math.BigDecimal amount) {
        if (!props.isAvailable()) {
            throw new BusinessException(ErrorCodes.BILLING_UNAVAILABLE,
                    "计费系统暂时不可用，交易已进入重试队列", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}