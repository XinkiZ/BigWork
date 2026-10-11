package com.wylie.registration;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.service.StudentQueryService;
import com.wylie.registration.web.dto.TranscriptView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 学业记录（Transcript）测试：按学期分组、学分与加权 GPA 汇总 */
class StudentTranscriptTest extends AbstractIntegrationTest {

    @Autowired
    private StudentQueryService studentQueryService;

    @Test
    @DisplayName("学业记录：按学期分组展示成绩并汇总学分与加权 GPA")
    void transcriptGroupedBySemester() {
        TranscriptView view = studentQueryService.transcript(1L);

        assertEquals("2026001", view.studentNo());
        assertEquals("陈晨", view.studentName());
        assertEquals(1, view.semesters().size(), "陈晨当前仅在上一学期有成绩");

        TranscriptView.SemesterSection section = view.semesters().get(0);
        assertEquals("2025-2026-2", section.code());
        assertEquals(3, section.rows().size());
        assertEquals(List.of("CS101", "CS201", "EN101"),
                section.rows().stream().map(TranscriptView.Row::courseNo).toList(), "课程号升序");
        assertEquals(8, section.creditsTotal(), "3 + 3 + 2 学分");
        // A(4*3) + B(3*3) + A(4*2) = 29 / 8 = 3.625 -> 3.63
        assertEquals(3.63, section.gpa());

        assertEquals(3, view.totalCourses());
        assertEquals(8, view.totalCredits());
        assertEquals(3.63, view.overallGpa());
        assertTrue(section.rows().stream().anyMatch(r -> "CS101".equals(r.courseNo()) && "A".equals(r.grade())));
    }

    @Test
    @DisplayName("学业记录：F 成绩计入学分但不计入 GPA 分子")
    void failedCourseCountsZeroPoints() {
        TranscriptView view = studentQueryService.transcript(5L);

        assertEquals(1, view.semesters().size());
        TranscriptView.SemesterSection section = view.semesters().get(0);
        assertEquals("F", section.rows().get(0).grade());
        assertEquals(3, section.creditsTotal());
        assertEquals(0.0, section.gpa(), "F 绩点按 0 计算");
        assertEquals(0.0, view.overallGpa());
    }

    @Test
    @DisplayName("学业记录：未录入成绩不计入 GPA，仍计入总学分")
    void pendingGradeExcludedFromGpa() {
        TranscriptView view = studentQueryService.transcript(8L);

        assertEquals(1, view.totalCourses());
        assertEquals(3, view.totalCredits(), "未录入成绩仍计入修读学分");
        assertNull(view.overallGpa(), "无已录入成绩时 GPA 为空");
        assertEquals("未录入", view.semesters().get(0).rows().get(0).grade());
    }

    @Test
    @DisplayName("学业记录：无任何成绩时返回空分组")
    void transcriptEmptyWhenNoGrades() {
        TranscriptView view = studentQueryService.transcript(9L);

        assertTrue(view.semesters().isEmpty());
        assertEquals(0, view.totalCourses());
        assertEquals(0, view.totalCredits());
        assertNull(view.overallGpa());
    }

    @Test
    @DisplayName("学业记录：学生不存在时提示错误")
    void transcriptUnknownStudentRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentQueryService.transcript(999L));
        assertEquals(ErrorCodes.NOT_FOUND, ex.getCode());
    }
}