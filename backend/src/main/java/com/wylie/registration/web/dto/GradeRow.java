package com.wylie.registration.web.dto;

/** 成绩录入行 */
public record GradeRow(Long studentId, String studentNo, String name, String gradeValue) {
}