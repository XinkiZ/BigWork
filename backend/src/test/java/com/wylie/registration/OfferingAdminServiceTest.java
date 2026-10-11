package com.wylie.registration;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.CourseOffering;
import com.wylie.registration.domain.EnrollmentStatus;
import com.wylie.registration.domain.OfferingStatus;
import com.wylie.registration.repo.CourseOfferingRepository;
import com.wylie.registration.repo.EnrollmentRepository;
import com.wylie.registration.service.OfferingAdminService;
import com.wylie.registration.web.dto.CourseOfferingView;
import com.wylie.registration.web.dto.OfferingUpsertRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 课程班（开课）管理测试：创建/编辑校验、教授资格与冲突、删除引用保护 */
class OfferingAdminServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OfferingAdminService offeringAdminService;

    @Autowired
    private CourseOfferingRepository offeringRepo;

    @Autowired
    private EnrollmentRepository enrollmentRepo;

    private BusinessException expectFailure(String code, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(code, ex.getCode(), "错误码不符，实际信息：" + ex.getMessage());
        return ex;
    }

    private OfferingUpsertRequest req(Long semesterId, String courseNo, int day, String start, String end,
                                      String room, Integer capacity, Integer minStudents, Long professorId) {
        return new OfferingUpsertRequest(semesterId, courseNo, day, LocalTime.parse(start), LocalTime.parse(end),
                room, capacity, minStudents, professorId);
    }

    @Test
    @DisplayName("开班：基于遗留课程目录创建课程班并自动填充课程信息")
    void createFromLegacyCourse() {
        CourseOfferingView view = offeringAdminService.create(
                req(2L, "CS301", 3, "14:00", "15:40", "A301", 10, 3, null));

        assertEquals("CS301", view.courseNo());
        assertEquals("软件工程", view.title());
        assertEquals(2, view.credits());
        assertEquals("PROPOSED", view.status());
        assertEquals(0, view.enrolledCount());
        assertTrue(view.prerequisites().stream().anyMatch(p -> p.courseNo().equals("CS102")),
                "软工应带先修课程 CS102");
    }

    @Test
    @DisplayName("开班：课程号不在遗留目录中时提示错误")
    void unknownCourseRejected() {
        expectFailure(ErrorCodes.VALIDATION, () -> offeringAdminService.create(
                req(2L, "XX999", 3, "14:00", "15:40", "A301", 10, 3, null)));
    }

    @Test
    @DisplayName("开班：容量须在 1-10 之间，最少人数不得大于容量")
    void capacityValidated() {
        expectFailure(ErrorCodes.VALIDATION, () -> offeringAdminService.create(
                req(2L, "CS301", 3, "14:00", "15:40", "A301", 11, 3, null)));
        expectFailure(ErrorCodes.VALIDATION, () -> offeringAdminService.create(
                req(2L, "CS301", 3, "14:00", "15:40", "A301", 10, 12, null)));
    }

    @Test
    @DisplayName("开班：开始时间必须早于结束时间")
    void timeOrderValidated() {
        expectFailure(ErrorCodes.VALIDATION, () -> offeringAdminService.create(
                req(2L, "CS301", 3, "15:40", "14:00", "A301", 10, 3, null)));
    }

    @Test
    @DisplayName("开班：已结束的学期不能开班")
    void pastSemesterRejected() {
        expectFailure(ErrorCodes.VALIDATION, () -> offeringAdminService.create(
                req(1L, "CS301", 3, "14:00", "15:40", "A301", 10, 3, null)));
    }

    @Test
    @DisplayName("开班：教授授课资格与时间冲突校验")
    void professorQualificationAndConflict() {
        // 李娜（P002）仅具备数学类资格，不能为 CS301 开班
        expectFailure(ErrorCodes.STATE_ERROR, () -> offeringAdminService.create(
                req(2L, "CS301", 3, "14:00", "15:40", "A301", 10, 3, 2L)));
        // 张伟（P001）具备 CS301 资格，但周一 08:30 与其已有 CS101（周一 08:00-09:40）冲突
        expectFailure(ErrorCodes.TIME_CONFLICT, () -> offeringAdminService.create(
                req(2L, "CS301", 1, "08:30", "09:30", "A301", 10, 3, 1L)));
    }

    @Test
    @DisplayName("编辑：容量不能低于当前已选人数")
    void updateCapacityBelowEnrolledRejected() {
        long enrolled = enrollmentRepo.countByOfferingIdAndStatus(1L, EnrollmentStatus.REGISTERED);
        assertTrue(enrolled > 0, "种子数据中 1 号课程班应有选课学生");
        expectFailure(ErrorCodes.VALIDATION, () -> offeringAdminService.update(1L,
                req(2L, "CS101", 1, "08:00", "09:40", "A102", (int) enrolled - 1, 3, 1L)));
    }

    @Test
    @DisplayName("编辑：已取消的课程班不能编辑")
    void cancelledOfferingNotEditable() {
        CourseOffering offering = offeringRepo.findById(2L).orElseThrow();
        offering.setStatus(OfferingStatus.CANCELLED);
        offeringRepo.save(offering);

        expectFailure(ErrorCodes.STATE_ERROR, () -> offeringAdminService.update(2L,
                req(2L, "CS102", 2, "10:00", "11:40", "A201", 10, 3, 3L)));
    }

    @Test
    @DisplayName("删除：存在选课引用的课程班不能删除；无引用的可删除")
    void deleteRules() {
        expectFailure(ErrorCodes.STATE_ERROR, () -> offeringAdminService.delete(1L));

        CourseOfferingView created = offeringAdminService.create(
                req(2L, "CS301", 4, "14:00", "15:40", "A301", 10, 3, null));
        offeringAdminService.delete(created.offeringId());
        assertTrue(offeringRepo.findById(created.offeringId()).isEmpty());
    }

    @Test
    @DisplayName("列表：按学期返回课程班（含人数与状态）")
    void listBySemester() {
        List<CourseOfferingView> first = offeringAdminService.list(1L);
        assertEquals(4, first.size());
        assertTrue(first.stream().allMatch(o -> "CONFIRMED".equals(o.status())));

        List<CourseOfferingView> second = offeringAdminService.list(2L);
        assertEquals(11, second.size());
    }

    @Test
    @DisplayName("遗留课程目录：返回全部课程与先修关系")
    void legacyCoursesListed() {
        var courses = offeringAdminService.legacyCourses();
        assertEquals(12, courses.size());
        var cs102 = courses.stream().filter(c -> c.courseNo().equals("CS102")).findFirst().orElseThrow();
        assertTrue(cs102.prerequisiteTitles().stream().anyMatch(t -> t.startsWith("CS101")));
    }
}