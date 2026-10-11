package com.wylie.registration.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record StudentUpsertRequest(
        @NotBlank(message = "姓名不能为空") String name,
        LocalDate birthDate,
        String ssn,
        String status,
        LocalDate graduationDate) {
}