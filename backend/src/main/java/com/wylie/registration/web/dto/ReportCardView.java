package com.wylie.registration.web.dto;

import java.util.List;

/** 成绩单视图 */
public record ReportCardView(String semesterName, String studentNo, String studentName,
                             boolean hasData, List<Row> rows) {

    public record Row(String courseNo, String title, int credits, String professorName, String grade) {
    }
}