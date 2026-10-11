package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.CourseOffering;
import com.wylie.registration.domain.Enrollment;
import com.wylie.registration.domain.EnrollmentStatus;
import com.wylie.registration.domain.Grade;
import com.wylie.registration.domain.GradeValue;
import com.wylie.registration.domain.OfferingStatus;
import com.wylie.registration.domain.Schedule;
import com.wylie.registration.domain.ScheduleItem;
import com.wylie.registration.domain.ScheduleItemStatus;
import com.wylie.registration.domain.ScheduleItemType;
import com.wylie.registration.domain.Semester;
import com.wylie.registration.domain.Student;
import com.wylie.registration.legacy.CatalogService;
import com.wylie.registration.legacy.CatalogSnapshot;
import com.wylie.registration.repo.CourseOfferingRepository;
import com.wylie.registration.repo.EnrollmentRepository;
import com.wylie.registration.repo.GradeRepository;
import com.wylie.registration.repo.ProfessorRepository;
import com.wylie.registration.repo.ScheduleItemRepository;
import com.wylie.registration.repo.ScheduleRepository;
import com.wylie.registration.repo.SemesterRepository;
import com.wylie.registration.repo.StudentRepository;
import com.wylie.registration.web.dto.CourseOfferingView;
import com.wylie.registration.web.dto.ReportCardView;
import com.wylie.registration.web.dto.ScheduleItemView;
import com.wylie.registration.web.dto.ScheduleView;
import com.wylie.registration.web.dto.TranscriptView;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 学生侧查询：课程目录、我的课表、成绩单、学业记录 */
@Service
public class StudentQueryService {

    private static final Map<String, Integer> GRADE_POINTS = Map.of(
            "A", 4, "B", 3, "C", 2, "D", 1, "F", 0);

    private final SemesterRepository semesterRepo;
    private final CourseOfferingRepository offeringRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final ScheduleRepository scheduleRepo;
    private final ScheduleItemRepository itemRepo;
    private final GradeRepository gradeRepo;
    private final StudentRepository studentRepo;
    private final ProfessorRepository professorRepo;
    private final CatalogService catalogService;
    private final OfferingQueryService offeringQuery;

    public StudentQueryService(SemesterRepository semesterRepo, CourseOfferingRepository offeringRepo,
                               EnrollmentRepository enrollmentRepo, ScheduleRepository scheduleRepo,
                               ScheduleItemRepository itemRepo, GradeRepository gradeRepo,
                               StudentRepository studentRepo, ProfessorRepository professorRepo,
                               CatalogService catalogService, OfferingQueryService offeringQuery) {
        this.semesterRepo = semesterRepo;
        this.offeringRepo = offeringRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.scheduleRepo = scheduleRepo;
        this.itemRepo = itemRepo;
        this.gradeRepo = gradeRepo;
        this.studentRepo = studentRepo;
        this.professorRepo = professorRepo;
        this.catalogService = catalogService;
        this.offeringQuery = offeringQuery;
    }

    public Semester currentSemester() {
        return semesterRepo.findByCurrentFlagTrue()
                .orElseThrow(() -> new BusinessException(ErrorCodes.STATE_ERROR, "系统未配置当前学期"));
    }

    public Semester semesterById(Long id) {
        return semesterRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "学期不存在"));
    }

    /** 上一学期：按开课日期取当前学期之前最近的学期（日期缺失时退回按 id 判断） */
    public Semester previousSemester() {
        Semester current = currentSemester();
        return semesterRepo.findAll().stream()
                .filter(s -> !s.getId().equals(current.getId()))
                .filter(s -> isBefore(s, current))
                .max(Comparator.comparing((Semester s) -> s.getStartDate() == null ? LocalDate.MIN : s.getStartDate())
                        .thenComparing(Semester::getId))
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "没有上一学期数据"));
    }

    private static boolean isBefore(Semester candidate, Semester current) {
        if (candidate.getStartDate() != null && current.getStartDate() != null) {
            return candidate.getStartDate().isBefore(current.getStartDate());
        }
        return candidate.getId() < current.getId();
    }

    public boolean adjustmentOpen(Semester semester) {
        if (semester.getAdjustmentStart() == null || semester.getAdjustmentEnd() == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return !today.isBefore(semester.getAdjustmentStart()) && !today.isAfter(semester.getAdjustmentEnd());
    }

    /** 已通过的课程号集合（A/B/C/D 视为通过），用于先修达成展示与提交校验 */
    public Set<String> passedCourseNos(Long studentId) {
        Set<Long> passedOfferingIds = gradeRepo.findByStudentId(studentId).stream()
                .filter(g -> g.getGradeValue() != null && GradeValue.PASSED.contains(g.getGradeValue()))
                .map(Grade::getOfferingId)
                .collect(Collectors.toSet());
        return offeringRepo.findAllById(passedOfferingIds).stream()
                .map(CourseOffering::getCourseNo)
                .collect(Collectors.toSet());
    }
    /** 可选的课程目录（遗留目录信息 + 当前学期课程班），目录不可用时抛出 503 */
    public List<CourseOfferingView> catalog(Long studentId) {
        CatalogSnapshot snapshot = catalogService.getCatalog();
        Semester semester = currentSemester();
        List<CourseOffering> offerings = offeringRepo.findBySemesterIdOrderByIdAsc(semester.getId()).stream()
                .filter(o -> o.getStatus() != OfferingStatus.CANCELLED)
                .toList();
        return offeringQuery.toViews(offerings, snapshot, passedCourseNos(studentId));
    }

    /** 我的课表（当前学期） */
    public ScheduleView mySchedule(Long studentId) {
        return mySchedule(studentId, null);
    }

    /** 我的课表（指定学期，不传则当前学期；历史学期用于只读查看） */
    public ScheduleView mySchedule(Long studentId, Long semesterId) {
        Semester semester = semesterId == null ? currentSemester() : semesterById(semesterId);
        Schedule schedule = scheduleRepo.findByStudentIdAndSemesterId(studentId, semester.getId()).orElse(null);
        if (schedule == null) {
            ScheduleView history = historySchedule(studentId, semester);
            if (history != null) {
                return history;
            }
            return new ScheduleView(null, "NONE", semester.getId(), semester.getName(),
                    semester.getRegistrationStatus().name(), adjustmentOpen(semester), adjustmentWindow(semester),
                    List.of(), null);
        }
        List<ScheduleItem> items = itemRepo.findByScheduleIdOrderByItemTypeAscSortOrderAsc(schedule.getId());
        List<Long> offeringIds = items.stream().map(ScheduleItem::getOfferingId).distinct().toList();
        Map<Long, CourseOffering> offeringMap = offeringRepo.findAllById(offeringIds).stream()
                .collect(Collectors.toMap(CourseOffering::getId, Function.identity()));
        CatalogSnapshot snapshot = null;
        try {
            snapshot = catalogService.getCatalog();
        } catch (RuntimeException ignored) {
            // 课程目录不可用时仍可展示本地课表（课程基本信息已在主库中）
        }
        Set<String> passedCourses = passedCourseNos(studentId);
        Map<Long, String> profNames = offeringQuery.professorNames();
        List<ScheduleItemView> views = new ArrayList<>();
        for (ScheduleItem item : items) {
            CourseOffering offering = offeringMap.get(item.getOfferingId());
            if (offering == null) {
                continue;
            }
            CourseOfferingView offeringView = offeringQuery.toViewWithNames(offering, snapshot, profNames, passedCourses);
            views.add(new ScheduleItemView(item.getId(), item.getItemType().name(), item.getSortOrder(),
                    item.getStatus().name(), Boolean.TRUE.equals(item.getPromoted()), offeringView));
        }
        return new ScheduleView(schedule.getId(), schedule.getStatus().name(), semester.getId(), semester.getName(),
                semester.getRegistrationStatus().name(), adjustmentOpen(semester), adjustmentWindow(semester),
                views, null);
    }
    /** 非当前学期没有选课计划行时，回退为“该学期已注册课程”的只读课表（历史学期查看） */
    private ScheduleView historySchedule(Long studentId, Semester semester) {
        if (Boolean.TRUE.equals(semester.getCurrentFlag())) {
            return null;
        }
        Set<Long> registeredOfferingIds = enrollmentRepo
                .findByStudentIdAndStatus(studentId, EnrollmentStatus.REGISTERED).stream()
                .map(Enrollment::getOfferingId)
                .collect(Collectors.toSet());
        if (registeredOfferingIds.isEmpty()) {
            return null;
        }
        List<CourseOffering> offerings = offeringRepo.findBySemesterIdOrderByIdAsc(semester.getId()).stream()
                .filter(offering -> registeredOfferingIds.contains(offering.getId()))
                .toList();
        if (offerings.isEmpty()) {
            return null;
        }
        CatalogSnapshot snapshot = null;
        try {
            snapshot = catalogService.getCatalog();
        } catch (RuntimeException ignored) {
            // 课程目录不可用时仍可展示本地课表（课程基本信息已在主库中）
        }
        Set<String> passedCourses = passedCourseNos(studentId);
        Map<Long, String> profNames = offeringQuery.professorNames();
        List<ScheduleItemView> views = new ArrayList<>();
        int sortOrder = 1;
        for (CourseOffering offering : offerings) {
            CourseOfferingView offeringView = offeringQuery.toViewWithNames(offering, snapshot, profNames, passedCourses);
            views.add(new ScheduleItemView(null, ScheduleItemType.PRIMARY.name(), sortOrder++,
                    ScheduleItemStatus.REGISTERED.name(), false, offeringView));
        }
        return new ScheduleView(null, "HISTORY", semester.getId(), semester.getName(),
                semester.getRegistrationStatus().name(), adjustmentOpen(semester), adjustmentWindow(semester),
                views, null);
    }

    /** 成绩单（默认上一学期，可指定任意学期） */
    public ReportCardView reportCard(Long studentId, Long semesterId) {
        Semester semester = semesterId == null ? previousSemester() : semesterById(semesterId);
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "学生不存在"));

        List<Grade> grades = gradeRepo.findByStudentId(studentId);
        Map<Long, CourseOffering> offeringMap = offeringRepo.findAllById(
                        grades.stream().map(Grade::getOfferingId).distinct().toList())
                .stream().collect(Collectors.toMap(CourseOffering::getId, Function.identity()));
        Map<Long, String> profNames = professorRepo.findAll().stream()
                .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));

        List<ReportCardView.Row> rows = new ArrayList<>();
        for (Grade grade : grades) {
            CourseOffering offering = offeringMap.get(grade.getOfferingId());
            if (offering == null || !offering.getSemesterId().equals(semester.getId())) {
                continue;
            }
            rows.add(new ReportCardView.Row(offering.getCourseNo(), offering.getTitle(), offering.getCredits(),
                    offering.getProfessorId() == null ? "-" : profNames.getOrDefault(offering.getProfessorId(), "-"),
                    grade.getGradeValue() == null ? "未录入" : grade.getGradeValue()));
        }
        rows.sort(Comparator.comparing(ReportCardView.Row::courseNo));
        return new ReportCardView(semester.getName(), student.getStudentNo(), student.getName(),
                !rows.isEmpty(), rows);
    }

    /** 学业记录：全部学期成绩按学期分组 + 学分与加权 GPA 汇总（I/未录入不计入 GPA） */
    public TranscriptView transcript(Long studentId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "学生不存在"));
        List<Grade> grades = gradeRepo.findByStudentId(studentId);
        Map<Long, CourseOffering> offeringMap = offeringRepo.findAllById(
                        grades.stream().map(Grade::getOfferingId).distinct().toList())
                .stream().collect(Collectors.toMap(CourseOffering::getId, Function.identity()));
        Map<Long, Semester> semesterMap = semesterRepo.findAll().stream()
                .collect(Collectors.toMap(Semester::getId, Function.identity()));
        Map<Long, String> profNames = professorRepo.findAll().stream()
                .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));

        Map<Long, List<TranscriptView.Row>> bySemester = new HashMap<>();
        for (Grade grade : grades) {
            CourseOffering offering = offeringMap.get(grade.getOfferingId());
            if (offering == null) {
                continue;
            }
            Semester semester = semesterMap.get(offering.getSemesterId());
            if (semester == null) {
                continue;
            }
            bySemester.computeIfAbsent(semester.getId(), key -> new ArrayList<>())
                    .add(new TranscriptView.Row(offering.getCourseNo(), offering.getTitle(), offering.getCredits(),
                            offering.getProfessorId() == null ? "-" : profNames.getOrDefault(offering.getProfessorId(), "-"),
                            grade.getGradeValue() == null ? "未录入" : grade.getGradeValue()));
        }

        List<TranscriptView.SemesterSection> sections = new ArrayList<>();
        int totalCourses = 0;
        int totalCredits = 0;
        double overallWeighted = 0;
        int overallGpaCredits = 0;
        List<Semester> ordered = semesterMap.values().stream()
                .filter(s -> bySemester.containsKey(s.getId()))
                .sorted(Comparator.comparing((Semester s) -> s.getStartDate() == null ? LocalDate.MIN : s.getStartDate())
                        .thenComparing(Semester::getId)
                        .reversed())
                .toList();
        for (Semester semester : ordered) {
            List<TranscriptView.Row> rows = bySemester.get(semester.getId()).stream()
                    .sorted(Comparator.comparing(TranscriptView.Row::courseNo))
                    .toList();
            int creditsTotal = rows.stream().mapToInt(TranscriptView.Row::credits).sum();
            double weighted = 0;
            int gpaCredits = 0;
            for (TranscriptView.Row row : rows) {
                Integer points = GRADE_POINTS.get(row.grade());
                if (points != null) {
                    weighted += (double) points * row.credits();
                    gpaCredits += row.credits();
                }
            }
            sections.add(new TranscriptView.SemesterSection(semester.getId(), semester.getCode(), semester.getName(),
                    rows, creditsTotal, gpaOf(weighted, gpaCredits)));
            totalCourses += rows.size();
            totalCredits += creditsTotal;
            overallWeighted += weighted;
            overallGpaCredits += gpaCredits;
        }
        return new TranscriptView(student.getStudentNo(), student.getName(), sections,
                totalCourses, totalCredits, gpaOf(overallWeighted, overallGpaCredits));
    }

    private static Double gpaOf(double weighted, int credits) {
        if (credits == 0) {
            return null;
        }
        return Math.round(weighted / credits * 100.0) / 100.0;
    }

    private String adjustmentWindow(Semester semester) {
        if (semester.getAdjustmentStart() == null || semester.getAdjustmentEnd() == null) {
            return null;
        }
        return semester.getAdjustmentStart() + " 至 " + semester.getAdjustmentEnd();
    }
}