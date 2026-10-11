package com.wylie.registration.domain;

/** 课程班状态 */
public enum OfferingStatus {
    /** 待确认（选课阶段） */
    PROPOSED,
    /** 已生效（关闭注册后确认） */
    CONFIRMED,
    /** 已取消 */
    CANCELLED
}