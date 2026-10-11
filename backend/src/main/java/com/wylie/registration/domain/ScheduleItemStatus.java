package com.wylie.registration.domain;

/** 课表条目状态 */
public enum ScheduleItemStatus {
    /** 已选择（暂存，不占用课程班名额） */
    SELECTED,
    /** 已注册（占用课程班名额） */
    REGISTERED,
    /** 已移除 */
    REMOVED
}