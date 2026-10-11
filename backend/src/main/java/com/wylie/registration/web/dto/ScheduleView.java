package com.wylie.registration.web.dto;

import java.util.List;

/** 学生课表视图 */
public record ScheduleView(
        Long scheduleId,
        String status,
        Long semesterId,
        String semesterName,
        String registrationStatus,
        boolean adjustmentOpen,
        String adjustmentWindow,
        List<ScheduleItemView> items,
        String notice) {
}