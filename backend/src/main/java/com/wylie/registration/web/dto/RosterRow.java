package com.wylie.registration.web.dto;

/** 选课名单行 */
public record RosterRow(Long studentId, String studentNo, String name, String grade) {
}