package com.wylie.registration.web.dto;

import java.time.LocalDate;

public record StudentDto(Long id, String studentNo, String name, LocalDate birthDate, String ssn,
                         String status, LocalDate graduationDate) {
}