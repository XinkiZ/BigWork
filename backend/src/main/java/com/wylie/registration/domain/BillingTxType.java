package com.wylie.registration.domain;

/** 计费交易类型 */
public enum BillingTxType {
    /** 关闭注册时的学期初始账单 */
    INITIAL,
    /** 选课调整期加退选差额 */
    ADJUSTMENT
}