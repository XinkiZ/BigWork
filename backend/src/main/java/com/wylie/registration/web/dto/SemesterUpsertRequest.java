package com.wylie.registration.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 学期新建/编辑请求 */
public record SemesterUpsertRequest(
        @NotBlank(message = "学期代码不能为空") String code,
        @NotBlank(message = "学期名称不能为空") String name,
        @NotNull(message = "开始日期不能为空") LocalDate startDate,
        @NotNull(message = "结束日期不能为空") LocalDate endDate,
        LocalDate adjustmentStart,
        LocalDate adjustmentEnd,
        String registrationStatus) {
}