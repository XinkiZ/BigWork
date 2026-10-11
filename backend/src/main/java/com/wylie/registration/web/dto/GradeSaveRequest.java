package com.wylie.registration.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GradeSaveRequest(@NotNull(message = "缺少成绩数据") List<GradeLine> grades) {

    public record GradeLine(Long studentId, String gradeValue) {
    }
}