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
import com.wylie.registration.legacy.CatalogService;
import com.wylie.registration.legacy.CatalogSnapshot;
import com.wylie.registration.legacy.LegacyCourse;
import com.wylie.registration.repo.CourseOfferingRepository;
import com.wylie.registration.repo.EnrollmentRepository;
import com.wylie.registration.repo.ScheduleItemRepository;
import com.wylie.registration.repo.ScheduleRepository;
import com.wylie.registration.web.dto.CourseOfferingView;
import com.wylie.registration.web.dto.ScheduleLine;
import com.wylie.registration.web.dto.ScheduleSubmitRequest;
import com.wylie.registration.web.dto.ScheduleView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 课程注册（Register for Courses）核心服务。
 *
 * <p>覆盖题目用例：创建/暂存/更新/删除课表，主选 4 门 + 备选 2 门；
 * 提交校验：先修要求、课程班未满且处于开放状态、无时间冲突；
 * 以及选课调整期的加退选（产生差额计费）。
 */
@Service
public class ScheduleService {

    private static final int MAX_PRIMARY = 4;
    private static final int MAX_BACKUP = 2;

    private final ScheduleRepository scheduleRepo;
    private final ScheduleItemRepository itemRepo;
    private final CourseOfferingRepository offeringRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final CatalogService catalogService;
    private final BillingService billingService;
    private final OfferingQueryService offeringQuery;
    private final StudentQueryService studentQuery;

    public ScheduleService(ScheduleRepository scheduleRepo, ScheduleItemRepository itemRepo,
                           CourseOfferingRepository offeringRepo, EnrollmentRepository enrollmentRepo,
                           CatalogService catalogService, BillingService billingService,
                           OfferingQueryService offeringQuery, StudentQueryService studentQuery) {
        this.scheduleRepo = scheduleRepo;
        this.itemRepo = itemRepo;
        this.offeringRepo = offeringRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.catalogService = catalogService;
        this.billingService = billingService;
        this.offeringQuery = offeringQuery;
        this.studentQuery = studentQuery;
    }

    private record ValidatedLine(CourseOffering offering, ScheduleItemType type, int order) {
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public List<CourseOfferingView> catalog(Long studentId) {
        return studentQuery.catalog(studentId);
    }

    public ScheduleView mySchedule(Long studentId) {
        return studentQuery.mySchedule(studentId);
    }

    /** 查看指定学期课表（不传则当前学期；历史学期用于只读查看） */
    public ScheduleView mySchedule(Long studentId, Long semesterId) {
        return studentQuery.mySchedule(studentId, semesterId);
    }

    // ------------------------------------------------------------------
    // 暂存 / 提交 / 删除
    // ------------------------------------------------------------------

    /** 暂存课表：课程标记为"已选择"（不占用课程班名额） */
    @Transactional
    public ScheduleView saveDraft(Long studentId, ScheduleSubmitRequest request) {
        Semester semester = studentQuery.currentSemester();
        ensureRegistrationOpen(semester);
        CatalogSnapshot snapshot = catalogService.getCatalog();
        List<ValidatedLine> lines = validate(studentId, semester, request.items(), snapshot, false);
        Schedule schedule = upsertSchedule(studentId, semester.getId(), ScheduleStatus.DRAFT);
        rebuildItems(schedule, lines, false);
        return studentQuery.mySchedule(studentId);
    }

    /** 提交课表：校验先修、满员、开放状态、时间冲突，通过后标记"已注册" */
    @Transactional
    public ScheduleView submit(Long studentId, ScheduleSubmitRequest request) {
        Semester semester = studentQuery.currentSemester();
        ensureRegistrationOpen(semester);
        CatalogSnapshot snapshot = catalogService.getCatalog();
        List<ValidatedLine> lines = validate(studentId, semester, request.items(), snapshot, true);
        Schedule schedule = upsertSchedule(studentId, semester.getId(), ScheduleStatus.SUBMITTED);
        rebuildItems(schedule, lines, true);
        syncEnrollments(studentId, semester, lines);
        return studentQuery.mySchedule(studentId);
    }

    /** 删除课表：同时将学生从对应课程班中移除 */
    @Transactional
    public void deleteSchedule(Long studentId) {
        Semester semester = studentQuery.currentSemester();
        ensureRegistrationOpen(semester);
        Schedule schedule = scheduleRepo.findByStudentIdAndSemesterId(studentId, semester.getId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "当前学期没有可删除的课表"));
        releaseEnrollments(studentId, semester, Set.of());
        itemRepo.deleteByScheduleId(schedule.getId());
        itemRepo.flush();
        scheduleRepo.delete(schedule);
    }

    // ------------------------------------------------------------------
    // 选课调整期（注册关闭后）：加课 / 退课，产生差额计费
    // ------------------------------------------------------------------

    @Transactional
    public ScheduleView adjustmentAdd(Long studentId, Long offeringId) {
        Semester semester = studentQuery.currentSemester();
        if (semester.getRegistrationStatus() != RegistrationStatus.CLOSED) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "本学期注册尚未关闭，请在选课页面直接调整");
        }
        if (!studentQuery.adjustmentOpen(semester)) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "当前不在选课调整期内，不能加退选");
        }
        CatalogSnapshot snapshot = catalogService.getCatalog();
        Schedule schedule = requireSubmittedSchedule(studentId, semester);

        CourseOffering offering = offeringRepo.findById(offeringId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "课程班不存在"));
        ensureOfferingUsable(offering, semester);
        if (offering.getStatus() != OfferingStatus.CONFIRMED) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "该课程班未生效（可能已取消），不能加选");
        }

        List<ScheduleItem> items = itemRepo.findByScheduleIdOrderByItemTypeAscSortOrderAsc(schedule.getId());
        long activePrimary = items.stream()
                .filter(i -> i.getItemType() == ScheduleItemType.PRIMARY && i.getStatus() == ScheduleItemStatus.REGISTERED)
                .count();
        if (activePrimary >= MAX_PRIMARY) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "主选课程已达 " + MAX_PRIMARY + " 门上限，请先退课再加课");
        }
        boolean alreadyChosen = items.stream().anyMatch(i -> i.getOfferingId().equals(offeringId)
                && i.getStatus() != ScheduleItemStatus.REMOVED);
        if (alreadyChosen) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "该课程已在你的课表中");
        }
        ensureCapacity(studentId, offering);
        checkPrerequisites(studentId, snapshot, List.of(offering));
        List<CourseOffering> current = items.stream()
                .filter(i -> i.getStatus() == ScheduleItemStatus.REGISTERED)
                .map(i -> offeringRepo.findById(i.getOfferingId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        checkConflictWith(current, offering);

        upsertEnrollment(offeringId, studentId);
        int nextOrder = items.stream()
                .filter(i -> i.getItemType() == ScheduleItemType.PRIMARY)
                .mapToInt(ScheduleItem::getSortOrder).max().orElse(0) + 1;
        ScheduleItem item = new ScheduleItem();
        item.setScheduleId(schedule.getId());
        item.setOfferingId(offeringId);
        item.setItemType(ScheduleItemType.PRIMARY);
        item.setSortOrder(Math.min(nextOrder, MAX_PRIMARY));
        item.setStatus(ScheduleItemStatus.REGISTERED);
        item.setPromoted(false);
        itemRepo.save(item);

        BigDecimal amount = billingService.tuitionFor(offering.getCredits());
        billingService.create(BillingTxType.ADJUSTMENT, studentId, semester.getId(), amount,
                "调整期加课：" + offering.getCourseNo() + " " + offering.getTitle());
        return withNotice(studentQuery.mySchedule(studentId),
                "调整期加课成功，已生成差额账单：+" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " 元");
    }

    @Transactional
    public ScheduleView adjustmentDrop(Long studentId, Long offeringId) {
        Semester semester = studentQuery.currentSemester();
        if (semester.getRegistrationStatus() != RegistrationStatus.CLOSED) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "本学期注册尚未关闭，请在选课页面直接调整");
        }
        if (!studentQuery.adjustmentOpen(semester)) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "当前不在选课调整期内，不能加退选");
        }
        Schedule schedule = requireSubmittedSchedule(studentId, semester);
        CourseOffering offering = offeringRepo.findById(offeringId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "课程班不存在"));
        ScheduleItem item = itemRepo.findByScheduleIdOrderByItemTypeAscSortOrderAsc(schedule.getId()).stream()
                .filter(i -> i.getOfferingId().equals(offeringId) && i.getStatus() == ScheduleItemStatus.REGISTERED)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "该课程不在你的已注册课表中"));

        Enrollment enrollment = enrollmentRepo.findByOfferingIdAndStudentId(offeringId, studentId).orElse(null);
        if (enrollment != null && enrollment.getStatus() == EnrollmentStatus.REGISTERED) {
            enrollment.setStatus(EnrollmentStatus.DROPPED);
            enrollmentRepo.save(enrollment);
        }
        item.setStatus(ScheduleItemStatus.REMOVED);
        itemRepo.save(item);

        BigDecimal amount = billingService.tuitionFor(offering.getCredits()).negate();
        billingService.create(BillingTxType.ADJUSTMENT, studentId, semester.getId(), amount,
                "调整期退课：" + offering.getCourseNo() + " " + offering.getTitle());
        return withNotice(studentQuery.mySchedule(studentId),
                "调整期退课成功，已生成退费差额账单：" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " 元");
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void ensureRegistrationOpen(Semester semester) {
        if (semester.getRegistrationStatus() != RegistrationStatus.OPEN) {
            throw new BusinessException(ErrorCodes.REGISTRATION_CLOSED, "本学期注册已关闭，暂不能选课");
        }
    }

    private Schedule requireSubmittedSchedule(Long studentId, Semester semester) {
        Schedule schedule = scheduleRepo.findByStudentIdAndSemesterId(studentId, semester.getId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "当前学期没有课表，无法调整"));
        if (schedule.getStatus() != ScheduleStatus.SUBMITTED) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "课表尚未提交，无法进行加退选");
        }
        return schedule;
    }

    private void ensureOfferingUsable(CourseOffering offering, Semester semester) {
        if (!offering.getSemesterId().equals(semester.getId())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "课程班不属于当前学期");
        }
        if (offering.getStatus() == OfferingStatus.CANCELLED) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "课程班已取消，不能选择");
        }
    }

    private Schedule upsertSchedule(Long studentId, Long semesterId, ScheduleStatus status) {
        Schedule schedule = scheduleRepo.findByStudentIdAndSemesterId(studentId, semesterId).orElseGet(Schedule::new);
        schedule.setStudentId(studentId);
        schedule.setSemesterId(semesterId);
        schedule.setStatus(status);
        return scheduleRepo.save(schedule);
    }

    private void rebuildItems(Schedule schedule, List<ValidatedLine> lines, boolean submitted) {
        itemRepo.deleteByScheduleId(schedule.getId());
        itemRepo.flush();
        for (ValidatedLine line : lines) {
            ScheduleItem item = new ScheduleItem();
            item.setScheduleId(schedule.getId());
            item.setOfferingId(line.offering().getId());
            item.setItemType(line.type());
            item.setSortOrder(line.order());
            item.setPromoted(false);
            boolean registered = submitted && line.type() == ScheduleItemType.PRIMARY;
            item.setStatus(registered ? ScheduleItemStatus.REGISTERED : ScheduleItemStatus.SELECTED);
            itemRepo.save(item);
        }
    }

    private List<ValidatedLine> validate(Long studentId, Semester semester, List<ScheduleLine> raw,
                                         CatalogSnapshot snapshot, boolean fullCheck) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "请至少选择 1 门主选课程");
        }
        List<ScheduleLine> primaries = new ArrayList<>();
        List<ScheduleLine> backups = new ArrayList<>();
        for (ScheduleLine line : raw) {
            if ("PRIMARY".equalsIgnoreCase(line.itemType())) {
                primaries.add(line);
            } else if ("BACKUP".equalsIgnoreCase(line.itemType())) {
                backups.add(line);
            } else {
                throw new BusinessException(ErrorCodes.VALIDATION, "课程类型仅支持主选(PRIMARY)/备选(BACKUP)");
            }
        }
        if (primaries.size() > MAX_PRIMARY) {
            throw new BusinessException(ErrorCodes.VALIDATION, "主选课程最多 " + MAX_PRIMARY + " 门");
        }
        if (backups.size() > MAX_BACKUP) {
            throw new BusinessException(ErrorCodes.VALIDATION, "备选课程最多 " + MAX_BACKUP + " 门");
        }
        if (primaries.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "请至少选择 1 门主选课程");
        }
        List<ValidatedLine> result = new ArrayList<>();
        Set<Long> offeringIds = new HashSet<>();
        Map<String, CourseOffering> courseMap = new HashMap<>();
        int order = 0;
        for (ScheduleLine line : primaries) {
            result.add(validateLine(studentId, semester, line, ScheduleItemType.PRIMARY, ++order,
                    offeringIds, courseMap));
        }
        order = 0;
        for (ScheduleLine line : backups) {
            result.add(validateLine(studentId, semester, line, ScheduleItemType.BACKUP, ++order,
                    offeringIds, courseMap));
        }
        if (fullCheck) {
            checkPrerequisites(studentId, snapshot, result.stream().map(ValidatedLine::offering).toList());
            checkTimeConflict(result.stream().map(ValidatedLine::offering).toList(), null);
        }
        return result;
    }

    private ValidatedLine validateLine(Long studentId, Semester semester, ScheduleLine line,
                                       ScheduleItemType type, int order,
                                       Set<Long> offeringIds, Map<String, CourseOffering> courseMap) {
        if (line.offeringId() == null) {
            throw new BusinessException(ErrorCodes.VALIDATION, "存在空的课程班选择");
        }
        CourseOffering offering = offeringRepo.findById(line.offeringId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "课程班不存在：" + line.offeringId()));
        ensureOfferingUsable(offering, semester);
        if (!offeringIds.add(offering.getId())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "同一课程班重复选择：" + offering.getTitle());
        }
        CourseOffering sameCourse = courseMap.putIfAbsent(offering.getCourseNo(), offering);
        if (sameCourse != null) {
            throw new BusinessException(ErrorCodes.DUPLICATE_COURSE,
                    "同一门课程只能选择一个课程班：" + offering.getCourseNo() + " " + offering.getTitle());
        }
        ensureCapacity(studentId, offering);
        return new ValidatedLine(offering, type, order);
    }

    /** 满员实时通知：选课过程中即校验容量并即时提示 */
    private void ensureCapacity(Long studentId, CourseOffering offering) {
        long count = offeringQuery.enrolledCount(offering.getId());
        Enrollment mine = enrollmentRepo.findByOfferingIdAndStudentId(offering.getId(), studentId).orElse(null);
        if (mine != null && mine.getStatus() == EnrollmentStatus.REGISTERED) {
            count--;
        }
        if (count >= offering.getCapacity()) {
            throw new BusinessException(ErrorCodes.COURSE_FULL,
                    "课程班【" + offering.getTitle() + "】已满员（" + count + "/" + offering.getCapacity()
                            + "），请改选其他课程");
        }
    }

    private void checkPrerequisites(Long studentId, CatalogSnapshot snapshot, List<CourseOffering> offerings) {
        Set<String> passedCourses = studentQuery.passedCourseNos(studentId);
        for (CourseOffering offering : offerings) {
            for (String prereq : snapshot.prerequisitesOf(offering.getCourseNo())) {
                if (!passedCourses.contains(prereq)) {
                    String title = Optional.ofNullable(snapshot.courses().get(prereq))
                            .map(LegacyCourse::title).orElse(prereq);
                    throw new BusinessException(ErrorCodes.PREREQUISITE_NOT_MET,
                            "课程【" + offering.getTitle() + "】要求先修【" + prereq + " " + title
                                    + "】，你尚未通过该课程");
                }
            }
        }
    }

    private void checkTimeConflict(List<CourseOffering> offerings, CourseOffering extra) {
        List<CourseOffering> all = new ArrayList<>(offerings);
        if (extra != null) {
            all.add(extra);
        }
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                CourseOffering a = all.get(i);
                CourseOffering b = all.get(j);
                if (overlap(a, b)) {
                    throw new BusinessException(ErrorCodes.TIME_CONFLICT,
                            "课程【" + a.getTitle() + "】与【" + b.getTitle() + "】上课时间冲突（"
                                    + ViewAssembler.dayText(a.getDayOfWeek()) + " "
                                    + a.getStartTime() + "-" + a.getEndTime() + "）");
                }
            }
        }
    }

    private void checkConflictWith(List<CourseOffering> current, CourseOffering candidate) {
        for (CourseOffering c : current) {
            if (overlap(c, candidate)) {
                throw new BusinessException(ErrorCodes.TIME_CONFLICT,
                        "课程【" + candidate.getTitle() + "】与已选课程【" + c.getTitle() + "】上课时间冲突（"
                                + ViewAssembler.dayText(c.getDayOfWeek()) + " "
                                + c.getStartTime() + "-" + c.getEndTime() + "）");
            }
        }
    }

    static boolean overlap(CourseOffering a, CourseOffering b) {
        if (!Objects.equals(a.getDayOfWeek(), b.getDayOfWeek())) {
            return false;
        }
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }

    private void syncEnrollments(Long studentId, Semester semester, List<ValidatedLine> lines) {
        Set<Long> registeredIds = lines.stream()
                .filter(l -> l.type() == ScheduleItemType.PRIMARY)
                .map(l -> l.offering().getId())
                .collect(Collectors.toSet());
        releaseEnrollments(studentId, semester, registeredIds);
        for (Long offeringId : registeredIds) {
            upsertEnrollment(offeringId, studentId);
        }
    }

    /** 释放不在保留名单中的本学期选课记录（置为已退课） */
    private void releaseEnrollments(Long studentId, Semester semester, Set<Long> keep) {
        Set<Long> semesterOfferingIds = offeringRepo.findBySemesterIdOrderByIdAsc(semester.getId()).stream()
                .map(CourseOffering::getId)
                .collect(Collectors.toSet());
        List<Enrollment> mine = enrollmentRepo.findByStudentIdAndStatus(studentId, EnrollmentStatus.REGISTERED);
        for (Enrollment enrollment : mine) {
            if (semesterOfferingIds.contains(enrollment.getOfferingId())
                    && !keep.contains(enrollment.getOfferingId())) {
                enrollment.setStatus(EnrollmentStatus.DROPPED);
                enrollmentRepo.save(enrollment);
            }
        }
    }

    private void upsertEnrollment(Long offeringId, Long studentId) {
        Enrollment enrollment = enrollmentRepo.findByOfferingIdAndStudentId(offeringId, studentId)
                .orElseGet(() -> {
                    Enrollment e = new Enrollment();
                    e.setOfferingId(offeringId);
                    e.setStudentId(studentId);
                    return e;
                });
        enrollment.setStatus(EnrollmentStatus.REGISTERED);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepo.save(enrollment);
    }

    private ScheduleView withNotice(ScheduleView view, String notice) {
        return new ScheduleView(view.scheduleId(), view.status(), view.semesterId(), view.semesterName(),
                view.registrationStatus(), view.adjustmentOpen(), view.adjustmentWindow(), view.items(), notice);
    }
}