package com.wylie.registration.web.dto;

import java.time.LocalDate;

/** 学期视图（学期下拉与学期管理共用） */
public record SemesterView(
        Long id,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String registrationStatus,
        LocalDate adjustmentStart,
        LocalDate adjustmentEnd,
        boolean currentFlag) {
}