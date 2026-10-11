package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.CourseOffering;
import com.wylie.registration.domain.Grade;
import com.wylie.registration.domain.OfferingStatus;
import com.wylie.registration.domain.RegistrationStatus;
import com.wylie.registration.domain.Schedule;
import com.wylie.registration.domain.ScheduleItem;
import com.wylie.registration.domain.Semester;
import com.wylie.registration.domain.Student;
import com.wylie.registration.legacy.CatalogService;
import com.wylie.registration.legacy.CatalogSnapshot;
import com.wylie.registration.repo.CourseOfferingRepository;
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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 学生侧查询：课程目录、我的课表、成绩单 */
@Service
public class StudentQueryService {

    private final SemesterRepository semesterRepo;
    private final CourseOfferingRepository offeringRepo;
    private final ScheduleRepository scheduleRepo;
    private final ScheduleItemRepository itemRepo;
    private final GradeRepository gradeRepo;
    private final StudentRepository studentRepo;
    private final ProfessorRepository professorRepo;
    private final CatalogService catalogService;
    private final OfferingQueryService offeringQuery;

    public StudentQueryService(SemesterRepository semesterRepo, CourseOfferingRepository offeringRepo,
                               ScheduleRepository scheduleRepo, ScheduleItemRepository itemRepo,
                               GradeRepository gradeRepo, StudentRepository studentRepo,
                               ProfessorRepository professorRepo, CatalogService catalogService,
                               OfferingQueryService offeringQuery) {
        this.semesterRepo = semesterRepo;
        this.offeringRepo = offeringRepo;
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

    public Semester previousSemester() {
        Semester current = currentSemester();
        return semesterRepo.findFirstByIdLessThanOrderByIdDesc(current.getId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "没有上一学期数据"));
    }

    public boolean adjustmentOpen(Semester semester) {
        if (semester.getAdjustmentStart() == null || semester.getAdjustmentEnd() == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return !today.isBefore(semester.getAdjustmentStart()) && !today.isAfter(semester.getAdjustmentEnd());
    }

    /** 可选的课程目录（遗留目录信息 + 本学期课程班），目录不可用时抛出 503 */
    public List<CourseOfferingView> catalog(Long studentId) {
        CatalogSnapshot snapshot = catalogService.getCatalog();
        Semester semester = currentSemester();
        List<CourseOffering> offerings = offeringRepo.findBySemesterIdOrderByIdAsc(semester.getId()).stream()
                .filter(o -> o.getStatus() != OfferingStatus.CANCELLED)
                .toList();
        return offeringQuery.toViews(offerings, snapshot);
    }

    /** 我的课表（当前学期） */
    public ScheduleView mySchedule(Long studentId) {
        Semester semester = currentSemester();
        Schedule schedule = scheduleRepo.findByStudentIdAndSemesterId(studentId, semester.getId()).orElse(null);
        if (schedule == null) {
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
        Map<Long, String> profNames = offeringQuery.professorNames();
        List<ScheduleItemView> views = new ArrayList<>();
        for (ScheduleItem item : items) {
            CourseOffering offering = offeringMap.get(item.getOfferingId());
            if (offering == null) {
                continue;
            }
            CourseOfferingView offeringView = offeringQuery.toViewWithNames(offering, snapshot, profNames);
            views.add(new ScheduleItemView(item.getId(), item.getItemType().name(), item.getSortOrder(),
                    item.getStatus().name(), Boolean.TRUE.equals(item.getPromoted()), offeringView));
        }
        return new ScheduleView(schedule.getId(), schedule.getStatus().name(), semester.getId(), semester.getName(),
                semester.getRegistrationStatus().name(), adjustmentOpen(semester), adjustmentWindow(semester),
                views, null);
    }

    /** 成绩单（默认上一学期） */
    public ReportCardView reportCard(Long studentId, Long semesterId) {
        Semester semester = semesterId == null
                ? previousSemester()
                : semesterRepo.findById(semesterId)
                    .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "学期不存在"));
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

    private String adjustmentWindow(Semester semester) {
        if (semester.getAdjustmentStart() == null || semester.getAdjustmentEnd() == null) {
            return null;
        }
        return semester.getAdjustmentStart() + " 至 " + semester.getAdjustmentEnd();
    }
}