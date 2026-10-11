package com.wylie.registration;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.service.StudentQueryService;
import com.wylie.registration.web.dto.ReportCardView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 查看成绩单（View Report Card）用例测试 */
class ReportCardTest extends AbstractIntegrationTest {

    @Autowired
    private StudentQueryService studentQueryService;

    @Test
    @DisplayName("查看成绩单：展示上一学期全部成绩")
    void reportCardOfPreviousSemester() {
        ReportCardView view = studentQueryService.reportCard(1L, null);

        assertEquals("2025-2026学年第二学期", view.semesterName());
        assertEquals("2026001", view.studentNo());
        assertTrue(view.hasData());
        assertEquals(3, view.rows().size(), "陈晨上一学期修读 3 门课程");
        assertTrue(view.rows().stream().allMatch(r -> r.grade() != null && !r.grade().isBlank()));
        assertTrue(view.rows().stream().anyMatch(r -> "CS101".equals(r.courseNo()) && "A".equals(r.grade())));
    }

    @Test
    @DisplayName("查看成绩单：未录入的成绩显示为未录入")
    void blankGradeShownAsPending() {
        ReportCardView view = studentQueryService.reportCard(8L, null);
        assertTrue(view.hasData());
        assertTrue(view.rows().stream().anyMatch(r -> "CS101".equals(r.courseNo()) && "未录入".equals(r.grade())));
    }

    @Test
    @DisplayName("查看成绩单：无成绩数据时提示无数据")
    void noGradeDataTerminatesGracefully() {
        ReportCardView view = studentQueryService.reportCard(9L, null);

        assertFalse(view.hasData(), "无成绩数据时 hasData=false");
        assertTrue(view.rows().isEmpty());
    }

    @Test
    @DisplayName("查看成绩单：当前学期尚未产生成绩")
    void currentSemesterHasNoGrades() {
        ReportCardView view = studentQueryService.reportCard(1L, 2L);
        assertFalse(view.hasData());
    }

    @Test
    @DisplayName("查看成绩单：学期不存在时提示错误")
    void unknownSemesterRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentQueryService.reportCard(1L, 999L));
        assertEquals(ErrorCodes.NOT_FOUND, ex.getCode());
    }
}