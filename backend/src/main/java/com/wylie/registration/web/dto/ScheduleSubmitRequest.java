package com.wylie.registration.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ScheduleSubmitRequest(@NotNull(message = "缺少课表内容") List<ScheduleLine> items) {
}