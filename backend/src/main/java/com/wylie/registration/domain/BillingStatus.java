package com.wylie.registration.domain;

/** 计费交易状态 */
public enum BillingStatus {
    /** 待发送 */
    PENDING,
    /** 已发送成功 */
    SENT,
    /** 发送失败（待重试） */
    FAILED
}