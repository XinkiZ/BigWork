package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.BillingTxType;
import com.wylie.registration.domain.CourseOffering;
import com.wylie.registration.domain.Enrollment;
import com.wylie.registration.domain.EnrollmentStatus;
import com.wylie.registration.domain.OfferingStatus;
import com.wylie.registration.domain.RegistrationStatus;
import com.wylie.registration.domain.Schedule;
import com.wylie.registration.domain.ScheduleItem;
import com.wylie.registration.domain.ScheduleItemStatus;
import com.wylie.registration.domain.ScheduleItemType;
import com.wylie.registration.domain.ScheduleStatus;
import com.wylie.registration.domain.Semester;
import com.wylie.registration.domain.Student;
import com.wylie.registration.repo.CourseOfferingRepository;
import com.wylie.registration.repo.EnrollmentRepository;
import com.wylie.registration.repo.ScheduleItemRepository;
import com.wylie.registration.repo.ScheduleRepository;
import com.wylie.registration.repo.SemesterRepository;
import com.wylie.registration.repo.StudentRepository;
import com.wylie.registration.web.dto.CloseRegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 关闭注册（Close Registration）批量工作流。
 *
 * <p>流程：校验注册状态 → 取消无教授/人数不足的课程班 → 课堂补全
 * （按序从备选课程补位）→ 二次校验并取消仍不足 3 人的课程班 →
 * 计算每位学生学费并向计费系统发送交易（失败由 BillingService 按周期重试）。
 */
@Service
public class CloseRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(CloseRegistrationService.class);
    private static final int MAX_PRIMARY = 4;

    private final SemesterRepository semesterRepo;
    private final CourseOfferingRepository offeringRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final ScheduleRepository scheduleRepo;
    private final ScheduleItemRepository itemRepo;
    private final StudentRepository studentRepo;
    private final BillingService billingService;

    public CloseRegistrationService(SemesterRepository semesterRepo, CourseOfferingRepository offeringRepo,
                                    EnrollmentRepository enrollmentRepo, ScheduleRepository scheduleRepo,
                                    ScheduleItemRepository itemRepo, StudentRepository studentRepo,
                                    BillingService billingService) {
        this.semesterRepo = semesterRepo;
        this.offeringRepo = offeringRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.scheduleRepo = scheduleRepo;
        this.itemRepo = itemRepo;
        this.studentRepo = studentRepo;
        this.billingService = billingService;
    }

    @Transactional
    public CloseRegistrationResult closeRegistration() {
        Semester semester = semesterRepo.findByCurrentFlagTrue()
                .orElseThrow(() -> new BusinessException(ErrorCodes.STATE_ERROR, "系统未配置当前学期"));
        if (semester.getRegistrationStatus() == RegistrationStatus.CLOSED) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "本学期注册已经关闭，无需重复执行");
        }

        List<String> messages = new ArrayList<>();
        List<CloseRegistrationResult.OfferingBrief> cancelled = new ArrayList<>();
        List<CloseRegistrationResult.PromotionRecord> promotions = new ArrayList<>();
        List<CloseRegistrationResult.BackupFailure> backupFailures = new ArrayList<>();
        List<CloseRegistrationResult.RemovedItem> removedItems = new ArrayList<>();

        List<CourseOffering> offerings = offeringRepo.findBySemesterIdOrderByIdAsc(semester.getId());
        Map<Long, CourseOffering> offeringMap = offerings.stream()
                .collect(Collectors.toMap(CourseOffering::getId, Function.identity()));
        List<Schedule> schedules = scheduleRepo.findBySemesterId(semester.getId());
        Map<Long, Schedule> scheduleMap = schedules.stream()
                .collect(Collectors.toMap(Schedule::getId, Function.identity()));
        Map<Long, Student> studentMap = studentRepo.findAll().stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        // ---- 第 1 遍：校验教授分配与最少开课人数 ----
        int confirmed = 0;
        for (CourseOffering offering : offerings) {
            if (offering.getStatus() == OfferingStatus.CANCELLED) {
                continue;
            }
            long count = enrolled(offering.getId());
            if (offering.getProfessorId() == null) {
                cancelOffering(offering, "未分配授课教授，课程班取消", cancelled, removedItems, scheduleMap, studentMap);
            } else if (count < offering.getMinStudents()) {
                cancelOffering(offering, "选课人数不足 " + offering.getMinStudents() + " 人（当前 " + count + " 人），课程班取消",
                        cancelled, removedItems, scheduleMap, studentMap);
            } else {
                offering.setStatus(OfferingStatus.CONFIRMED);
                offeringRepo.save(offering);
                confirmed++;
            }
        }

        // ---- 第 2 遍：课表补全（主选不足 4 门时，按顺序启用备选课程） ----
        for (Schedule schedule : schedules) {
            if (schedule.getStatus() != ScheduleStatus.SUBMITTED) {
                continue;
            }
            Student student = studentMap.get(schedule.getStudentId());
            String studentNo = student == null ? "?" : student.getStudentNo();
            String studentName = student == null ? "?" : student.getName();
            List<ScheduleItem> items = itemRepo.findByScheduleIdOrderByItemTypeAscSortOrderAsc(schedule.getId());
            long primaryCount = items.stream()
                    .filter(i -> i.getItemType() == ScheduleItemType.PRIMARY
                            && i.getStatus() == ScheduleItemStatus.REGISTERED)
                    .count();
            if (primaryCount >= MAX_PRIMARY) {
                continue;
            }
            List<CourseOffering> registered = items.stream()
                    .filter(i -> i.getStatus() == ScheduleItemStatus.REGISTERED)
                    .map(i -> offeringMap.get(i.getOfferingId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));

            List<ScheduleItem> backups = items.stream()
                    .filter(i -> i.getItemType() == ScheduleItemType.BACKUP
                            && i.getStatus() == ScheduleItemStatus.SELECTED)
                    .sorted(Comparator.comparingInt(ScheduleItem::getSortOrder))
                    .toList();

            for (ScheduleItem backup : backups) {
                if (primaryCount >= MAX_PRIMARY) {
                    break;
                }
                CourseOffering offering = offeringMap.get(backup.getOfferingId());
                if (offering == null || offering.getStatus() != OfferingStatus.CONFIRMED) {
                    backupFailures.add(new CloseRegistrationResult.BackupFailure(studentNo, studentName,
                            offering == null ? "?" : offering.getCourseNo(),
                            offering == null ? "?" : offering.getTitle(), "课程班已取消或未生效，备选未启用"));
                    continue;
                }
                long count = enrolled(offering.getId());
                if (count >= offering.getCapacity()) {
                    backupFailures.add(new CloseRegistrationResult.BackupFailure(studentNo, studentName,
                            offering.getCourseNo(), offering.getTitle(),
                            "课程班已满员（" + count + "/" + offering.getCapacity() + "），备选未启用"));
                    continue;
                }
                boolean conflict = registered.stream().anyMatch(r -> ScheduleService.overlap(r, offering));
                if (conflict) {
                    backupFailures.add(new CloseRegistrationResult.BackupFailure(studentNo, studentName,
                            offering.getCourseNo(), offering.getTitle(), "与已注册课程时间冲突，备选未启用"));
                    continue;
                }
                Enrollment enrollment = enrollmentRepo
                        .findByOfferingIdAndStudentId(offering.getId(), schedule.getStudentId())
                        .orElseGet(() -> {
                            Enrollment e = new Enrollment();
                            e.setOfferingId(offering.getId());
                            e.setStudentId(schedule.getStudentId());
                            return e;
                        });
                enrollment.setStatus(EnrollmentStatus.REGISTERED);
                enrollmentRepo.save(enrollment);
                backup.setStatus(ScheduleItemStatus.REGISTERED);
                backup.setPromoted(true);
                itemRepo.save(backup);
                registered.add(offering);
                primaryCount++;
                promotions.add(new CloseRegistrationResult.PromotionRecord(studentNo, studentName,
                        offering.getCourseNo(), offering.getTitle(), backup.getSortOrder()));
            }
        }

        // ---- 第 3 遍：二次校验（补位后仍不足 3 人的课程班取消），循环至稳定 ----
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 5) {
            changed = false;
            for (CourseOffering offering : offerings) {
                if (offering.getStatus() != OfferingStatus.CONFIRMED) {
                    continue;
                }
                long count = enrolled(offering.getId());
                if (count < offering.getMinStudents()) {
                    cancelOffering(offering, "补位后选课人数仍不足 " + offering.getMinStudents() + " 人（当前 "
                            + count + " 人），课程班取消", cancelled, removedItems, scheduleMap, studentMap);
                    confirmed--;
                    changed = true;
                }
            }
        }

        // ---- 第 4 遍：计算学费并登记计费交易 ----
        int billed = 0;
        int txCount = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Schedule schedule : schedules) {
            if (schedule.getStatus() != ScheduleStatus.SUBMITTED) {
                continue;
            }
            List<ScheduleItem> items = itemRepo.findByScheduleIdOrderByItemTypeAscSortOrderAsc(schedule.getId());
            Set<Long> registeredIds = items.stream()
                    .filter(i -> i.getStatus() == ScheduleItemStatus.REGISTERED)
                    .map(ScheduleItem::getOfferingId)
                    .collect(Collectors.toSet());
            int credits = registeredIds.stream()
                    .map(offeringMap::get)
                    .filter(Objects::nonNull)
                    .mapToInt(CourseOffering::getCredits)
                    .sum();
            if (credits <= 0) {
                continue;
            }
            BigDecimal amount = billingService.tuitionFor(credits);
            billingService.create(BillingTxType.INITIAL, schedule.getStudentId(), semester.getId(), amount,
                    semester.getName() + " 账单：共 " + credits + " 学分");
            billed++;
            txCount++;
            total = total.add(amount);
        }

        semester.setRegistrationStatus(RegistrationStatus.CLOSED);
        semesterRepo.save(semester);

        messages.add("注册已关闭：" + semester.getName());
        messages.add("已生效课程班 " + confirmed + " 个；取消课程班 " + cancelled.size() + " 个");
        messages.add("备选补位成功 " + promotions.size() + " 条；补位失败 " + backupFailures.size() + " 条");
        messages.add("生成计费交易 " + txCount + " 笔，合计 " + total + " 元；计费系统不可用时将自动重试发送");
        log.info("关闭注册完成：semester={}, confirmed={}, cancelled={}, promotions={}, billed={}",
                semester.getCode(), confirmed, cancelled.size(), promotions.size(), billed);

        return new CloseRegistrationResult(semester.getId(), semester.getName(),
                LocalDateTime.now().toString(), cancelled, confirmed, promotions, backupFailures,
                removedItems, billed, total, txCount, messages);
    }

    private long enrolled(Long offeringId) {
        return enrollmentRepo.countByOfferingIdAndStatus(offeringId, EnrollmentStatus.REGISTERED);
    }

    /** 取消课程班：同步移除选课记录与课表关联 */
    private void cancelOffering(CourseOffering offering, String reason,
                                List<CloseRegistrationResult.OfferingBrief> cancelled,
                                List<CloseRegistrationResult.RemovedItem> removedItems,
                                Map<Long, Schedule> scheduleMap, Map<Long, Student> studentMap) {
        offering.setStatus(OfferingStatus.CANCELLED);
        offeringRepo.save(offering);
        cancelled.add(new CloseRegistrationResult.OfferingBrief(offering.getId(), offering.getCourseNo(),
                offering.getTitle(), reason));
        for (ScheduleItem item : itemRepo.findByOfferingId(offering.getId())) {
            if (item.getStatus() == ScheduleItemStatus.REGISTERED) {
                item.setStatus(ScheduleItemStatus.REMOVED);
                itemRepo.save(item);
                Schedule schedule = scheduleMap.get(item.getScheduleId());
                Student student = schedule == null ? null : studentMap.get(schedule.getStudentId());
                removedItems.add(new CloseRegistrationResult.RemovedItem(
                        student == null ? "?" : student.getStudentNo(),
                        student == null ? "?" : student.getName(),
                        offering.getCourseNo(), offering.getTitle(), item.getItemType().name()));
            }
        }
        for (Enrollment enrollment : enrollmentRepo.findByOfferingIdAndStatus(offering.getId(),
                EnrollmentStatus.REGISTERED)) {
            enrollment.setStatus(EnrollmentStatus.DROPPED);
            enrollmentRepo.save(enrollment);
        }
    }
}