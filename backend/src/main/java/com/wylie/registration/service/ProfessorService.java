package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.CourseOffering;
import com.wylie.registration.domain.Enrollment;
import com.wylie.registration.domain.EnrollmentStatus;
import com.wylie.registration.domain.Grade;
import com.wylie.registration.domain.GradeValue;
import com.wylie.registration.domain.OfferingStatus;
import com.wylie.registration.domain.Professor;
import com.wylie.registration.domain.RegistrationStatus;
import com.wylie.registration.domain.Semester;
import com.wylie.registration.domain.Student;
import com.wylie.registration.legacy.CatalogService;
import com.wylie.registration.legacy.CatalogSnapshot;
import com.wylie.registration.legacy.LegacyCourse;
import com.wylie.registration.repo.CourseOfferingRepository;
import com.wylie.registration.repo.EnrollmentRepository;
import com.wylie.registration.repo.GradeRepository;
import com.wylie.registration.repo.ProfessorRepository;
import com.wylie.registration.repo.StudentRepository;
import com.wylie.registration.web.dto.CourseOfferingView;
import com.wylie.registration.web.dto.GradeRow;
import com.wylie.registration.web.dto.GradeSaveRequest;
import com.wylie.registration.web.dto.QualifiedCourseView;
import com.wylie.registration.web.dto.RosterRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 教授侧用例：选择授课课程、查看选课名单、录入成绩 */
@Service
public class ProfessorService {

    private final ProfessorRepository professorRepo;
    private final CourseOfferingRepository offeringRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final GradeRepository gradeRepo;
    private final StudentRepository studentRepo;
    private final CatalogService catalogService;
    private final OfferingQueryService offeringQuery;
    private final StudentQueryService studentQuery;

    public ProfessorService(ProfessorRepository professorRepo, CourseOfferingRepository offeringRepo,
                            EnrollmentRepository enrollmentRepo, GradeRepository gradeRepo,
                            StudentRepository studentRepo, CatalogService catalogService,
                            OfferingQueryService offeringQuery, StudentQueryService studentQuery) {
        this.professorRepo = professorRepo;
        this.offeringRepo = offeringRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.gradeRepo = gradeRepo;
        this.studentRepo = studentRepo;
        this.catalogService = catalogService;
        this.offeringQuery = offeringQuery;
        this.studentQuery = studentQuery;
    }

    // ------------------------------------------------------------------
    // 选择授课课程
    // ------------------------------------------------------------------

    public List<QualifiedCourseView> qualifiedCourses(Long professorId) {
        Professor professor = requireProfessor(professorId);
        CatalogSnapshot snapshot = catalogService.getCatalog();
        Semester semester = studentQuery.currentSemester();
        Set<String> qualified = snapshot.coursesQualifiedFor(professor.getProfessorNo());
        Map<String, List<CourseOffering>> byCourse = offeringRepo
                .findBySemesterIdOrderByIdAsc(semester.getId()).stream()
                .filter(o -> o.getStatus() != OfferingStatus.CANCELLED)
                .collect(Collectors.groupingBy(CourseOffering::getCourseNo));
        Map<Long, String> names = offeringQuery.professorNames();
        List<QualifiedCourseView> views = new ArrayList<>();
        for (String courseNo : qualified.stream().sorted().toList()) {
            LegacyCourse course = snapshot.courses().get(courseNo);
            if (course == null) {
                continue;
            }
            List<CourseOffering> offerings = byCourse.getOrDefault(courseNo, List.of());
            List<CourseOfferingView> offeringViews = offerings.stream()
                    .map(o -> offeringQuery.toViewWithNames(o, snapshot, names))
                    .toList();
            List<String> prereqTitles = snapshot.prerequisitesOf(courseNo).stream()
                    .map(no -> {
                        LegacyCourse c = snapshot.courses().get(no);
                        return c == null ? no : no + " " + c.title();
                    })
                    .sorted()
                    .toList();
            views.add(new QualifiedCourseView(courseNo, course.title(), course.credits(),
                    course.department(), prereqTitles, offeringViews));
        }
        return views;
    }

    /** 我在指定学期的授课课程班（不传则当前学期；历史学期用于名单查看与成绩补录） */
    public List<CourseOfferingView> myOfferings(Long professorId, Long semesterId) {
        Semester semester = semesterId == null ? studentQuery.currentSemester() : studentQuery.semesterById(semesterId);
        List<CourseOffering> offerings = offeringRepo
                .findBySemesterIdAndProfessorIdOrderByIdAsc(semester.getId(), professorId);
        return offeringQuery.toViews(offerings, safeSnapshot());
    }

    @Transactional
    public CourseOfferingView selectOffering(Long professorId, Long offeringId) {
        Semester semester = studentQuery.currentSemester();
        if (semester.getRegistrationStatus() != RegistrationStatus.OPEN) {
            throw new BusinessException(ErrorCodes.REGISTRATION_CLOSED, "注册已关闭，不能调整授课安排");
        }
        Professor professor = requireProfessor(professorId);
        CourseOffering offering = requireOffering(offeringId);
        if (!offering.getSemesterId().equals(semester.getId())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "课程班不属于当前学期");
        }
        CatalogSnapshot snapshot = catalogService.getCatalog();
        if (!snapshot.coursesQualifiedFor(professor.getProfessorNo()).contains(offering.getCourseNo())) {
            throw new BusinessException(ErrorCodes.STATE_ERROR,
                    "你不具备【" + offering.getTitle() + "】的授课资格");
        }
        if (offering.getProfessorId() != null && !offering.getProfessorId().equals(professorId)) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "该课程班已由其他教授选择");
        }
        List<CourseOffering> mine = offeringRepo
                .findBySemesterIdAndProfessorIdOrderByIdAsc(semester.getId(), professorId).stream()
                .filter(o -> !o.getId().equals(offeringId) && o.getStatus() != OfferingStatus.CANCELLED)
                .toList();
        for (CourseOffering other : mine) {
            if (ScheduleService.overlap(other, offering)) {
                throw new BusinessException(ErrorCodes.TIME_CONFLICT,
                        "与已选授课课程【" + other.getTitle() + "】时间冲突（"
                                + ViewAssembler.dayText(other.getDayOfWeek()) + " "
                                + other.getStartTime() + "-" + other.getEndTime() + "）");
            }
        }
        offering.setProfessorId(professorId);
        offeringRepo.save(offering);
        return offeringQuery.toViewWithNames(offering, snapshot, offeringQuery.professorNames());
    }

    @Transactional
    public CourseOfferingView unselectOffering(Long professorId, Long offeringId) {
        Semester semester = studentQuery.currentSemester();
        if (semester.getRegistrationStatus() != RegistrationStatus.OPEN) {
            throw new BusinessException(ErrorCodes.REGISTRATION_CLOSED, "注册已关闭，不能调整授课安排");
        }
        CourseOffering offering = requireOffering(offeringId);
        if (!Objects.equals(offering.getProfessorId(), professorId)) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "该课程班不是你当前选择的授课课程");
        }
        offering.setProfessorId(null);
        offeringRepo.save(offering);
        return offeringQuery.toView(offering, null);
    }

    // ------------------------------------------------------------------
    // 选课名单 / 成绩
    // ------------------------------------------------------------------

    public List<RosterRow> roster(Long professorId, Long offeringId) {
        CourseOffering offering = verifyOwner(professorId, offeringId);
        Map<Long, Grade> grades = gradeRepo.findByOfferingId(offering.getId()).stream()
                .collect(Collectors.toMap(Grade::getStudentId, Function.identity(), (a, b) -> a));
        return enrollmentRepo.findByOfferingIdAndStatus(offering.getId(), EnrollmentStatus.REGISTERED).stream()
                .map(Enrollment::getStudentId)
                .distinct()
                .map(id -> studentRepo.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Student::getStudentNo))
                .map(s -> new RosterRow(s.getId(), s.getStudentNo(), s.getName(),
                        grades.containsKey(s.getId()) ? grades.get(s.getId()).getGradeValue() : null))
                .toList();
    }

    public List<GradeRow> gradeSheet(Long professorId, Long offeringId) {
        CourseOffering offering = verifyOwner(professorId, offeringId);
        requireGradeableSemester(offering);
        Map<Long, Grade> grades = gradeRepo.findByOfferingId(offering.getId()).stream()
                .collect(Collectors.toMap(Grade::getStudentId, Function.identity(), (a, b) -> a));
        return enrollmentRepo.findByOfferingIdAndStatus(offering.getId(), EnrollmentStatus.REGISTERED).stream()
                .map(Enrollment::getStudentId)
                .distinct()
                .map(id -> studentRepo.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Student::getStudentNo))
                .map(s -> new GradeRow(s.getId(), s.getStudentNo(), s.getName(),
                        grades.containsKey(s.getId()) ? grades.get(s.getId()).getGradeValue() : null))
                .toList();
    }

    /** 录入/修改成绩：仅授课教授本人、仅已开始的历史学期课程班、支持留空补录 */
    @Transactional
    public List<GradeRow> saveGrades(Long professorId, Long offeringId, GradeSaveRequest request) {
        CourseOffering offering = verifyOwner(professorId, offeringId);
        requireGradeableSemester(offering);
        Set<Long> roster = enrollmentRepo
                .findByOfferingIdAndStatus(offering.getId(), EnrollmentStatus.REGISTERED).stream()
                .map(Enrollment::getStudentId)
                .collect(Collectors.toSet());
        for (GradeSaveRequest.GradeLine line : request.grades()) {
            if (!GradeValue.isValid(line.gradeValue())) {
                throw new BusinessException(ErrorCodes.VALIDATION, "成绩等级仅支持 A/B/C/D/F/I 或留空");
            }
            if (!roster.contains(line.studentId())) {
                throw new BusinessException(ErrorCodes.VALIDATION, "学生不在该课程班名单中，不能录入成绩");
            }
            Grade grade = gradeRepo.findByOfferingIdAndStudentId(offering.getId(), line.studentId())
                    .orElseGet(() -> {
                        Grade g = new Grade();
                        g.setOfferingId(offering.getId());
                        g.setStudentId(line.studentId());
                        return g;
                    });
            grade.setGradeValue(line.gradeValue() == null || line.gradeValue().isBlank()
                    ? null : line.gradeValue().trim().toUpperCase());
            grade.setProfessorId(professorId);
            grade.setUpdatedAt(LocalDateTime.now());
            gradeRepo.save(grade);
        }
        return gradeSheet(professorId, offeringId);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private Professor requireProfessor(Long professorId) {
        return professorRepo.findById(professorId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "教授不存在"));
    }

    private CourseOffering requireOffering(Long offeringId) {
        return offeringRepo.findById(offeringId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "课程班不存在"));
    }

    private CourseOffering verifyOwner(Long professorId, Long offeringId) {
        CourseOffering offering = requireOffering(offeringId);
        if (!Objects.equals(offering.getProfessorId(), professorId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "你不是该课程班的授课教授，无权操作", HttpStatus.FORBIDDEN);
        }
        return offering;
    }

    /** 成绩可录入范围：目标学期已开始且非当前学期（默认为上一学期，支持为更早学期补录） */
    private void requireGradeableSemester(CourseOffering offering) {
        Semester semester = studentQuery.semesterById(offering.getSemesterId());
        Semester current = studentQuery.currentSemester();
        if (semester.getId().equals(current.getId())) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "当前学期尚未结束，暂不能录入成绩");
        }
        if (semester.getStartDate() == null || semester.getStartDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "该学期尚未开始，不能录入成绩");
        }
    }

    private CatalogSnapshot safeSnapshot() {
        try {
            return catalogService.getCatalog();
        } catch (RuntimeException e) {
            return null;
        }
    }
}