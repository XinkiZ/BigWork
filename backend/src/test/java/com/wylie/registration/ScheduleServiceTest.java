package com.wylie.registration;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.EnrollmentStatus;
import com.wylie.registration.domain.RegistrationStatus;
import com.wylie.registration.domain.Schedule;
import com.wylie.registration.domain.ScheduleItemStatus;
import com.wylie.registration.domain.ScheduleStatus;
import com.wylie.registration.legacy.LegacyUnavailableException;
import com.wylie.registration.repo.EnrollmentRepository;
import com.wylie.registration.repo.ScheduleItemRepository;
import com.wylie.registration.repo.ScheduleRepository;
import com.wylie.registration.repo.SemesterRepository;
import com.wylie.registration.service.ScheduleService;
import com.wylie.registration.web.dto.ScheduleLine;
import com.wylie.registration.web.dto.ScheduleSubmitRequest;
import com.wylie.registration.web.dto.ScheduleView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 课程注册（Register for Courses）业务规则测试。
 *
 * <p>覆盖：主选 4 门 + 备选 2 门限制、先修校验、满员实时通知、时间冲突、
 * 注册关闭拦截、课程目录不可用、暂存/提交/删除课表。
 */
class ScheduleServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private SemesterRepository semesterRepo;

    @Autowired
    private ScheduleRepository scheduleRepo;

    @Autowired
    private ScheduleItemRepository itemRepo;

    @Autowired
    private EnrollmentRepository enrollmentRepo;

    private ScheduleSubmitRequest request(ScheduleLine... lines) {
        return new ScheduleSubmitRequest(List.of(lines));
    }

    private ScheduleLine primary(long offeringId) {
        return new ScheduleLine(offeringId, "PRIMARY");
    }

    private ScheduleLine backup(long offeringId) {
        return new ScheduleLine(offeringId, "BACKUP");
    }

    private BusinessException expectFailure(String code, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(code, ex.getCode(), "错误码不符，实际信息：" + ex.getMessage());
        return ex;
    }

    @Test
    @DisplayName("暂存课表：课程标记为已选择，不占用课程班名额")
    void saveDraftKeepsSeatsFree() {
        long before = enrollmentRepo.countByOfferingIdAndStatus(5L, EnrollmentStatus.REGISTERED);
        ScheduleView view = scheduleService.saveDraft(1L, request(primary(5L), backup(10L)));

        assertEquals("DRAFT", view.status());
        assertEquals(2, view.items().size());
        assertTrue(view.items().stream().allMatch(i -> "SELECTED".equals(i.status())));
        assertEquals(before, enrollmentRepo.countByOfferingIdAndStatus(5L, EnrollmentStatus.REGISTERED),
                "暂存不应产生选课记录");
    }

    @Test
    @DisplayName("提交课表：主选标记为已注册并写入选课名单")
    void submitRegistersPrimaryCourses() {
        long before = enrollmentRepo.countByOfferingIdAndStatus(5L, EnrollmentStatus.REGISTERED);
        ScheduleView view = scheduleService.submit(4L, request(primary(5L), backup(10L)));

        assertEquals("SUBMITTED", view.status());
        assertEquals(before + 1,
                enrollmentRepo.countByOfferingIdAndStatus(5L, EnrollmentStatus.REGISTERED),
                "提交后选课名单应增加 1 人");
        assertTrue(scheduleRepo.findByStudentIdAndSemesterId(4L, 2L).isPresent());
    }

    @Test
    @DisplayName("先修校验：未通过先修课程的学生不能选择后续课程")
    void prerequisiteIsEnforced() {
        // 李思远（学号 2026005）上一学期 CS101 成绩为 F，未通过
        BusinessException ex = expectFailure(ErrorCodes.PREREQUISITE_NOT_MET,
                () -> scheduleService.submit(5L, request(primary(2L))));
        assertTrue(ex.getMessage().contains("先修"), "应提示先修要求：" + ex.getMessage());
    }

    @Test
    @DisplayName("先修校验：已通过先修课程的学生可以正常选课")
    void prerequisitePassedAllowsRegistration() {
        ScheduleView view = scheduleService.submit(1L, request(primary(2L)));
        assertEquals("SUBMITTED", view.status());
    }

    @Test
    @DisplayName("满员实时通知：提交前即提示课程班已满")
    void fullOfferingRejectedBeforeSubmit() {
        // 9 号课程班（大学物理）已满 10/10
        BusinessException ex = expectFailure(ErrorCodes.COURSE_FULL,
                () -> scheduleService.submit(6L, request(primary(9L))));
        assertTrue(ex.getMessage().contains("已满员"), "应实时提示满员：" + ex.getMessage());
    }

    @Test
    @DisplayName("时间冲突：同一时段的课程不能同时选择")
    void timeConflictRejected() {
        // 1 号（程序设计基础）与 11 号（计算机导论）均为周一 08:00-09:40
        BusinessException ex = expectFailure(ErrorCodes.TIME_CONFLICT,
                () -> scheduleService.submit(1L, request(primary(1L), primary(11L))));
        assertTrue(ex.getMessage().contains("冲突"), "应提示时间冲突：" + ex.getMessage());
    }

    @Test
    @DisplayName("主选最多 4 门、备选最多 2 门")
    void selectionLimitsEnforced() {
        expectFailure(ErrorCodes.VALIDATION, () -> scheduleService.submit(1L, request(
                primary(1L), primary(2L), primary(3L), primary(5L), primary(7L))));
        expectFailure(ErrorCodes.VALIDATION, () -> scheduleService.submit(1L, request(
                primary(1L), backup(2L), backup(3L), backup(5L))));
    }

    @Test
    @DisplayName("注册已关闭：不能提交或修改课表")
    void closedRegistrationRejected() {
        var semester = semesterRepo.findById(2L).orElseThrow();
        semester.setRegistrationStatus(RegistrationStatus.CLOSED);
        semesterRepo.save(semester);

        expectFailure(ErrorCodes.REGISTRATION_CLOSED,
                () -> scheduleService.submit(1L, request(primary(5L))));
        expectFailure(ErrorCodes.REGISTRATION_CLOSED,
                () -> scheduleService.saveDraft(1L, request(primary(5L))));
    }

    @Test
    @DisplayName("课程目录不可用：选课终止并提示错误")
    void catalogUnavailableTerminatesUseCase() {
        legacyProperties.setAvailable(false);
        assertThrows(LegacyUnavailableException.class,
                () -> scheduleService.saveDraft(1L, request(primary(5L))));
    }

    @Test
    @DisplayName("删除课表：课表下线且选课记录被释放")
    void deleteScheduleReleasesEnrollments() {
        long before = enrollmentRepo.countByOfferingIdAndStatus(9L, EnrollmentStatus.REGISTERED);
        scheduleService.deleteSchedule(1L);

        assertTrue(scheduleRepo.findByStudentIdAndSemesterId(1L, 2L).isEmpty(), "课表应被删除");
        assertEquals(before - 1, enrollmentRepo.countByOfferingIdAndStatus(9L, EnrollmentStatus.REGISTERED),
                "大学物理名单应减少 1 人");
    }

    @Test
    @DisplayName("更新课表：再次提交覆盖旧的选课结果并释放原课程名额")
    void submitUpdatesExistingSchedule() {
        long maBefore = enrollmentRepo.countByOfferingIdAndStatus(5L, EnrollmentStatus.REGISTERED);

        scheduleService.submit(1L, request(primary(2L), primary(3L)));

        Schedule schedule = scheduleRepo.findByStudentIdAndSemesterId(1L, 2L).orElseThrow();
        var items = itemRepo.findByScheduleIdOrderByItemTypeAscSortOrderAsc(schedule.getId());

        assertEquals(2, items.size());
        assertEquals(ScheduleStatus.SUBMITTED, schedule.getStatus());
        assertTrue(items.stream().allMatch(i -> i.getStatus() == ScheduleItemStatus.REGISTERED));
        assertEquals(maBefore - 1,
                enrollmentRepo.countByOfferingIdAndStatus(5L, EnrollmentStatus.REGISTERED),
                "旧课表中的高等数学 I 应被释放");

    }
}