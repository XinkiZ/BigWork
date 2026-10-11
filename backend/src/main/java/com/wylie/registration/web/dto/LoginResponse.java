package com.wylie.registration.web.dto;

public record LoginResponse(String token, String role, Long id, String name, String username,
                            Long currentSemesterId, String currentSemesterName, String registrationStatus) {
}