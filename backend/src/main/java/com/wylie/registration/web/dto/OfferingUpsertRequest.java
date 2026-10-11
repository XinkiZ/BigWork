package com.wylie.registration.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** 课程班新建/编辑请求（编辑时学期与课程号不可变更） */
public record OfferingUpsertRequest(
        Long semesterId,
        @NotBlank(message = "课程号不能为空") String courseNo,
        @NotNull(message = "上课星期不能为空") Integer dayOfWeek,
        @NotNull(message = "开始时间不能为空") LocalTime startTime,
        @NotNull(message = "结束时间不能为空") LocalTime endTime,
        String room,
        Integer capacity,
        Integer minStudents,
        Long professorId) {
}