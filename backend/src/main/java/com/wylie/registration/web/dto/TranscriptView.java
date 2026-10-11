package com.wylie.registration.web.dto;

import java.util.List;

/** 学业记录（全部学期成绩汇总，按学期分组） */
public record TranscriptView(
        String studentNo,
        String studentName,
        List<SemesterSection> semesters,
        int totalCourses,
        int totalCredits,
        Double overallGpa) {

    public record SemesterSection(Long semesterId, String code, String name,
                                  List<Row> rows, int creditsTotal, Double gpa) {
    }

    public record Row(String courseNo, String title, int credits, String professorName, String grade) {
    }
}