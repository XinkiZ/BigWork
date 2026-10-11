package com.wylie.registration;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.BillingStatus;
import com.wylie.registration.domain.BillingTxType;
import com.wylie.registration.domain.EnrollmentStatus;
import com.wylie.registration.domain.OfferingStatus;
import com.wylie.registration.domain.RegistrationStatus;
import com.wylie.registration.domain.ScheduleItemStatus;
import com.wylie.registration.repo.BillingTransactionRepository;
import com.wylie.registration.repo.CourseOfferingRepository;
import com.wylie.registration.repo.EnrollmentRepository;
import com.wylie.registration.repo.ScheduleItemRepository;
import com.wylie.registration.repo.ScheduleRepository;
import com.wylie.registration.repo.SemesterRepository;
import com.wylie.registration.service.BillingService;
import com.wylie.registration.service.CloseRegistrationService;
import com.wylie.registration.web.dto.CloseRegistrationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 关闭注册（Close Registration）批量工作流测试 */
class CloseRegistrationServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CloseRegistrationService closeRegistrationService;

    @Autowired
    private SemesterRepository semesterRepo;

    @Autowired
    private CourseOfferingRepository offeringRepo;

    @Autowired
    private ScheduleRepository scheduleRepo;

    @Autowired
    private ScheduleItemRepository itemRepo;

    @Autowired
    private EnrollmentRepository enrollmentRepo;

    @Autowired
    private BillingTransactionRepository txRepo;

    @Autowired
    private BillingService billingService;

    @Test
    @DisplayName("关闭注册：取消无教授与人数不足的课程班")
    void cancelsInvalidOfferings() {
        CloseRegistrationResult result = closeRegistrationService.closeRegistration();

        assertTrue(result.cancelledOfferings().stream().anyMatch(o -> o.offeringId().equals(4L)),
                "0 人的课程班应被取消");
        assertTrue(result.cancelledOfferings().stream().anyMatch(o -> o.offeringId().equals(6L)),
                "仅 1 人的课程班应被取消");
        assertTrue(result.cancelledOfferings().stream().anyMatch(o -> o.offeringId().equals(10L)),
                "未分配教授的课程班应被取消");

        assertEquals(OfferingStatus.CANCELLED, offeringRepo.findById(4L).orElseThrow().getStatus());
        assertEquals(OfferingStatus.CANCELLED, offeringRepo.findById(6L).orElseThrow().getStatus());
        assertEquals(OfferingStatus.CANCELLED, offeringRepo.findById(10L).orElseThrow().getStatus());
        assertEquals(OfferingStatus.CONFIRMED, offeringRepo.findById(1L).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("关闭注册：主选不足 4 门时按备选顺序补位")
    void promotesBackupInOrder() {
        CloseRegistrationResult result = closeRegistrationService.closeRegistration();

        // 郑爽（2026010）主选中的线性代数被取消，备选第 1 顺位高等数学 I 补位成功
        assertTrue(result.promotions().stream().anyMatch(p -> p.studentNo().equals("2026010")
                        && p.courseNo().equals("MA101") && p.order() == 1),
                "备选第一顺位应补位成功：" + result.promotions());

        var promoted = itemRepo.findByOfferingId(5L).stream()
                .filter(i -> i.getItemType().name().equals("BACKUP"))
                .filter(i -> Boolean.TRUE.equals(i.getPromoted()))
                .count();
        assertTrue(promoted >= 1, "补位后的备选条目应标记 promoted=1");
        assertEquals(EnrollmentStatus.REGISTERED,
                enrollmentRepo.findByOfferingIdAndStudentId(5L, 10L).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("关闭注册：备选课程满员时补位失败并记录原因")
    void reportsBackupFailureWhenFull() {
        CloseRegistrationResult result = closeRegistrationService.closeRegistration();

        // 赵雪、孙浩的备选均为已满的大学物理（10/10），应补位失败
        assertTrue(result.backupFailures().stream().anyMatch(f -> f.courseNo().equals("PH101")),
                "满员备选应记录失败原因：" + result.backupFailures());
        assertTrue(result.backupFailures().stream()
                        .anyMatch(f -> f.reason().contains("满员") && f.reason().contains("10/10")),
                "失败原因应说明满员人数");
    }

    @Test
    @DisplayName("关闭注册：计算学费并生成计费交易（500 元/学分）")
    void createsBillingTransactions() {
        CloseRegistrationResult result = closeRegistrationService.closeRegistration();

        assertTrue(result.billedStudentCount() > 0, "应为学生生成账单");
        assertEquals(result.billingTransactionCount(), result.billedStudentCount());
        assertEquals(0, billingService.tuitionFor(4).compareTo(new BigDecimal("2000")),
                "4 学分课程学费应为 2000 元");

        var transactions = txRepo.findBySemesterIdOrderByIdAsc(2L);
        assertEquals(result.billingTransactionCount(), transactions.size());
        assertTrue(transactions.stream().allMatch(t -> t.getTxType() == BillingTxType.INITIAL));
        assertTrue(transactions.stream().allMatch(t -> t.getAmount().compareTo(BigDecimal.ZERO) > 0));
    }

    @Test
    @DisplayName("关闭注册：学期状态置为已关闭，且不能重复执行")
    void closesSemesterAndRejectsSecondRun() {
        closeRegistrationService.closeRegistration();

        assertEquals(RegistrationStatus.CLOSED, semesterRepo.findById(2L).orElseThrow().getRegistrationStatus());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> closeRegistrationService.closeRegistration());
        assertEquals(ErrorCodes.STATE_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("已经关闭"));
    }

    @Test
    @DisplayName("关闭注册：被取消课程班的学生课表条目置为已移除")
    void removesItemsOfCancelledOfferings() {
        closeRegistrationService.closeRegistration();

        long removed = itemRepo.findByOfferingId(6L).stream()
                .filter(i -> i.getStatus() == ScheduleItemStatus.REMOVED)
                .count();
        assertEquals(1, removed, "线性代数（1 人）取消后该学生的课表条目应置为已移除");
        long schedules = scheduleRepo.findBySemesterId(2L).size();
        assertEquals(16, schedules, "课表本身保留，供调整期加退选使用");
    }
}