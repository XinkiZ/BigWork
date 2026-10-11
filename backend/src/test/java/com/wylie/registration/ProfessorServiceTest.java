package com.wylie.registration;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.RegistrationStatus;
import com.wylie.registration.repo.CourseOfferingRepository;
import com.wylie.registration.repo.SemesterRepository;
import com.wylie.registration.service.ProfessorService;
import com.wylie.registration.web.dto.CourseOfferingView;
import com.wylie.registration.web.dto.GradeRow;
import com.wylie.registration.web.dto.GradeSaveRequest;
import com.wylie.registration.web.dto.QualifiedCourseView;
import com.wylie.registration.web.dto.RosterRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 教授用例测试：选择授课课程、查看选课名单、提交成绩 */
class ProfessorServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ProfessorService professorService;

    @Autowired
    private CourseOfferingRepository offeringRepo;

    @Autowired
    private SemesterRepository semesterRepo;

    private BusinessException expectFailure(String code, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(code, ex.getCode(), "错误码不符，实际信息：" + ex.getMessage());
        return ex;
    }

    private GradeSaveRequest grades(GradeSaveRequest.GradeLine... lines) {
        return new GradeSaveRequest(List.of(lines));
    }

    // ------------------------- 选择授课 -------------------------

    @Test
    @DisplayName("选择授课：仅展示教授具备资格的课程")
    void qualifiedCoursesComeFromLegacyCatalog() {
        List<QualifiedCourseView> courses = professorService.qualifiedCourses(1L);
        List<String> courseNos = courses.stream().map(QualifiedCourseView::courseNo).toList();

        assertEquals(List.of("CS101", "CS102", "CS201", "CS202", "CS301"), courseNos);
        QualifiedCourseView dataStructure = courses.stream()
                .filter(c -> c.courseNo().equals("CS102")).findFirst().orElseThrow();
        assertTrue(dataStructure.prerequisiteTitles().stream().anyMatch(t -> t.contains("CS101")),
                "数据结构应带先修课程 CS101");
    }

    @Test
    @DisplayName("选择授课：无授课资格时提示错误")
    void qualificationIsEnforced() {
        // 李娜（P002）仅具备数学类课程资格
        expectFailure(ErrorCodes.STATE_ERROR, () -> professorService.selectOffering(2L, 1L));
    }

    @Test
    @DisplayName("选择授课：课程班已被其他教授选择时提示错误")
    void alreadyTakenByAnotherProfessor() {
        expectFailure(ErrorCodes.STATE_ERROR, () -> professorService.selectOffering(1L, 2L));
    }

    @Test
    @DisplayName("选择授课：时间冲突的课程班不能选择")
    void teachingTimeConflictDetected() {
        // 程序设计基础（周一 08:00）当前无授课教授；王强（P003）已选择计算机导论（周一 08:00），应判定冲突
        var offering = offeringRepo.findById(1L).orElseThrow();
        offering.setProfessorId(null);
        offeringRepo.save(offering);

        BusinessException ex = expectFailure(ErrorCodes.TIME_CONFLICT,
                () -> professorService.selectOffering(3L, 1L));
        assertTrue(ex.getMessage().contains("冲突"));
    }

    @Test
    @DisplayName("选择授课：无冲突时成功登记授课教授")
    void selectOfferingSuccess() {
        var offering = offeringRepo.findById(4L).orElseThrow();
        offering.setProfessorId(null);
        offeringRepo.save(offering);

        CourseOfferingView view = professorService.selectOffering(1L, 4L);

        assertEquals(1L, view.professorId());
        assertEquals("张伟", view.professorName());
        assertTrue(professorService.myOfferings(1L).stream().anyMatch(o -> o.offeringId().equals(4L)));
    }

    @Test
    @DisplayName("选择授课：取消选择后课程班回到未分配状态")
    void unselectOffering() {
        professorService.unselectOffering(1L, 1L);
        assertNull(offeringRepo.findById(1L).orElseThrow().getProfessorId());
    }

    @Test
    @DisplayName("选择授课：注册关闭后不能调整授课安排")
    void cannotAdjustAfterRegistrationClosed() {
        var semester = semesterRepo.findById(2L).orElseThrow();
        semester.setRegistrationStatus(RegistrationStatus.CLOSED);
        semesterRepo.save(semester);

        expectFailure(ErrorCodes.REGISTRATION_CLOSED, () -> professorService.selectOffering(1L, 4L));
    }

    // ------------------------- 选课名单 -------------------------

    @Test
    @DisplayName("选课名单：仅授课教授可查看本人课程班的名单")
    void rosterVisibleOnlyForOwner() {
        List<RosterRow> roster = professorService.roster(1L, 12L);
        assertEquals(6, roster.size());
        assertEquals("2026001", roster.get(0).studentNo());

        expectFailure(ErrorCodes.FORBIDDEN, () -> professorService.roster(4L, 12L));
    }

    // ------------------------- 提交成绩 -------------------------

    @Test
    @DisplayName("提交成绩：展示上学期课程班名单与已录入成绩")
    void gradeSheetShowsRosterAndGrades() {
        List<GradeRow> rows = professorService.gradeSheet(1L, 13L);
        assertEquals(4, rows.size());
        assertTrue(rows.stream().anyMatch(r -> r.studentNo().equals("2026003") && r.gradeValue() == null),
                "未录入的成绩应留空展示");
        assertTrue(rows.stream().anyMatch(r -> r.studentNo().equals("2026002") && "A".equals(r.gradeValue())));
    }

    @Test
    @DisplayName("提交成绩：支持留空补录与修改")
    void saveBlankAndModifyGrades() {
        professorService.saveGrades(1L, 12L, grades(new GradeSaveRequest.GradeLine(8L, "A")));
        assertTrue(professorService.gradeSheet(1L, 12L).stream()
                .anyMatch(r -> r.studentNo().equals("2026008") && "A".equals(r.gradeValue())),
                "留空成绩应可补录");

        professorService.saveGrades(1L, 12L, grades(new GradeSaveRequest.GradeLine(1L, "B")));
        assertTrue(professorService.gradeSheet(1L, 12L).stream()
                .anyMatch(r -> r.studentNo().equals("2026001") && "B".equals(r.gradeValue())),
                "已录入成绩应可修改");

        professorService.saveGrades(1L, 12L, grades(new GradeSaveRequest.GradeLine(1L, null)));
        assertTrue(professorService.gradeSheet(1L, 12L).stream()
                .anyMatch(r -> r.studentNo().equals("2026001") && r.gradeValue() == null),
                "成绩应可置空");
    }

    @Test
    @DisplayName("提交成绩：等级只能是 A/B/C/D/F/I 或留空")
    void gradeValueValidated() {
        expectFailure(ErrorCodes.VALIDATION,
                () -> professorService.saveGrades(1L, 12L, grades(new GradeSaveRequest.GradeLine(1L, "X"))));
    }

    @Test
    @DisplayName("提交成绩：非本课程班学生不能录入")
    void gradeTargetMustBeInRoster() {
        expectFailure(ErrorCodes.VALIDATION,
                () -> professorService.saveGrades(1L, 12L, grades(new GradeSaveRequest.GradeLine(6L, "A"))));
    }

    @Test
    @DisplayName("提交成绩：仅授课教授可录入（越权返回 403）")
    void onlyOwnerProfessorCanSubmit() {
        expectFailure(ErrorCodes.FORBIDDEN,
                () -> professorService.saveGrades(4L, 12L, grades(new GradeSaveRequest.GradeLine(1L, "A"))));
    }

    @Test
    @DisplayName("提交成绩：只能录入上一学期课程班")
    void onlyPreviousSemesterAllowed() {
        expectFailure(ErrorCodes.STATE_ERROR,
                () -> professorService.saveGrades(1L, 1L, grades(new GradeSaveRequest.GradeLine(6L, "A"))));
    }
}