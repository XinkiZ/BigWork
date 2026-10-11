package com.wylie.registration.web.dto;

import java.time.LocalDate;

public record ProfessorDto(Long id, String professorNo, String name, LocalDate birthDate, String ssn,
                           String status, String department) {
}