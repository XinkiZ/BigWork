package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.CourseOffering;
import com.wylie.registration.domain.EnrollmentStatus;
import com.wylie.registration.domain.OfferingStatus;
import com.wylie.registration.domain.Professor;
import com.wylie.registration.domain.Semester;
import com.wylie.registration.legacy.CatalogService;
import com.wylie.registration.legacy.CatalogSnapshot;
import com.wylie.registration.legacy.LegacyCourse;
import com.wylie.registration.repo.CourseOfferingRepository;
import com.wylie.registration.repo.EnrollmentRepository;
import com.wylie.registration.repo.GradeRepository;
import com.wylie.registration.repo.ProfessorRepository;
import com.wylie.registration.repo.ScheduleItemRepository;
import com.wylie.registration.repo.SemesterRepository;
import com.wylie.registration.web.dto.CourseOfferingView;
import com.wylie.registration.web.dto.LegacyCourseView;
import com.wylie.registration.web.dto.OfferingUpsertRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 注册管理员：课程班（开课）管理。
 *
 * <p>课程主数据仍来自遗留课程目录（只读）；本服务只维护主库中的"课程班"：
 * 学期、时间、教室、容量与授课教授分配。
 */
@Service
public class OfferingAdminService {

    private static final int MAX_CAPACITY = 10;
    private static final int DEFAULT_MIN_STUDENTS = 3;

    private final CourseOfferingRepository offeringRepo;
    private final SemesterRepository semesterRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final ScheduleItemRepository itemRepo;
    private final GradeRepository gradeRepo;
    private final ProfessorRepository professorRepo;
    private final CatalogService catalogService;
    private final OfferingQueryService offeringQuery;

    public OfferingAdminService(CourseOfferingRepository offeringRepo, SemesterRepository semesterRepo,
                                EnrollmentRepository enrollmentRepo, ScheduleItemRepository itemRepo,
                                GradeRepository gradeRepo, ProfessorRepository professorRepo,
                                CatalogService catalogService, OfferingQueryService offeringQuery) {
        this.offeringRepo = offeringRepo;
        this.semesterRepo = semesterRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.itemRepo = itemRepo;
        this.gradeRepo = gradeRepo;
        this.professorRepo = professorRepo;
        this.catalogService = catalogService;
        this.offeringQuery = offeringQuery;
    }

    public List<CourseOfferingView> list(Long semesterId) {
        Semester semester = requireSemester(semesterId);
        return offeringQuery.toViews(offeringRepo.findBySemesterIdOrderByIdAsc(semester.getId()), safeSnapshot());
    }

    /** 遗留课程目录列表（供开班下拉选择） */
    public List<LegacyCourseView> legacyCourses() {
        CatalogSnapshot snapshot = catalogService.getCatalog();
        return snapshot.courses().values().stream()
                .sorted(Comparator.comparing(LegacyCourse::courseNo))
                .map(c -> new LegacyCourseView(c.courseNo(), c.title(), c.credits(), c.department(),
                        snapshot.prerequisitesOf(c.courseNo()).stream().sorted().toList()))
                .toList();
    }

    @Transactional
    public CourseOfferingView create(OfferingUpsertRequest request) {
        Semester semester = requireSemester(request.semesterId());
        if (semester.getEndDate() != null && semester.getEndDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "该学期已结束，不能开班");
        }
        validateFields(request);
        CatalogSnapshot snapshot = catalogService.getCatalog();
        LegacyCourse course = snapshot.courses().get(request.courseNo());
        if (course == null) {
            throw new BusinessException(ErrorCodes.VALIDATION, "课程号不在课程目录中：" + request.courseNo());
        }
        int capacity = request.capacity() == null ? MAX_CAPACITY : request.capacity();
        int minStudents = request.minStudents() == null ? DEFAULT_MIN_STUDENTS : request.minStudents();
        ensureCapacityRange(capacity, minStudents, 0);
        if (request.professorId() != null) {
            checkProfessor(snapshot, semester.getId(), request, null);
        }

        CourseOffering offering = new CourseOffering();
        offering.setSemesterId(semester.getId());
        offering.setCourseNo(course.courseNo());
        offering.setTitle(course.title());
        offering.setCredits(course.credits());
        offering.setDepartment(course.department());
        offering.setProfessorId(request.professorId());
        offering.setDayOfWeek(request.dayOfWeek());
        offering.setStartTime(request.startTime());
        offering.setEndTime(request.endTime());
        offering.setRoom(request.room());
        offering.setCapacity(capacity);
        offering.setMinStudents(minStudents);
        offering.setStatus(OfferingStatus.PROPOSED);
        offeringRepo.save(offering);
        return offeringQuery.toViews(List.of(offering), snapshot).get(0);
    }
    @Transactional
    public CourseOfferingView update(Long id, OfferingUpsertRequest request) {
        CourseOffering offering = requireOffering(id);
        if (offering.getStatus() == OfferingStatus.CANCELLED) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "已取消的课程班不能编辑");
        }
        validateFields(request);
        long enrolled = enrollmentRepo.countByOfferingIdAndStatus(offering.getId(), EnrollmentStatus.REGISTERED);
        int capacity = request.capacity() == null ? offering.getCapacity() : request.capacity();
        int minStudents = request.minStudents() == null ? offering.getMinStudents() : request.minStudents();
        ensureCapacityRange(capacity, minStudents, enrolled);
        CatalogSnapshot snapshot = request.professorId() != null ? catalogService.getCatalog() : safeSnapshot();
        if (request.professorId() != null) {
            checkProfessor(snapshot, offering.getSemesterId(), request, offering.getId());
        }
        offering.setProfessorId(request.professorId());
        offering.setDayOfWeek(request.dayOfWeek());
        offering.setStartTime(request.startTime());
        offering.setEndTime(request.endTime());
        offering.setRoom(request.room());
        offering.setCapacity(capacity);
        offering.setMinStudents(minStudents);
        offeringRepo.save(offering);
        return offeringQuery.toViews(List.of(offering), snapshot).get(0);
    }

    /** 删除课程班：仅当无选课、课表、成绩任何引用（不级联删除） */
    @Transactional
    public void delete(Long id) {
        CourseOffering offering = requireOffering(id);
        if (enrollmentRepo.existsByOfferingId(offering.getId())) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "该课程班存在选课记录，不能删除");
        }
        if (!itemRepo.findByOfferingId(offering.getId()).isEmpty()) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "该课程班已被学生课表引用，不能删除");
        }
        if (!gradeRepo.findByOfferingId(offering.getId()).isEmpty()) {
            throw new BusinessException(ErrorCodes.STATE_ERROR, "该课程班已存在成绩记录，不能删除");
        }
        offeringRepo.delete(offering);
    }

    private void validateFields(OfferingUpsertRequest request) {
        if (request.dayOfWeek() < 1 || request.dayOfWeek() > 7) {
            throw new BusinessException(ErrorCodes.VALIDATION, "上课星期必须是 1（周一）至 7（周日）");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "开始时间必须早于结束时间");
        }
    }

    private void ensureCapacityRange(int capacity, int minStudents, long enrolled) {
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new BusinessException(ErrorCodes.VALIDATION, "课程班容量必须在 1-" + MAX_CAPACITY + " 人之间");
        }
        if (capacity < enrolled) {
            throw new BusinessException(ErrorCodes.VALIDATION, "容量不能小于当前已选人数（" + enrolled + " 人）");
        }
        if (minStudents < 1 || minStudents > capacity) {
            throw new BusinessException(ErrorCodes.VALIDATION, "最少开课人数必须在 1 至容量之间");
        }
    }

    private void checkProfessor(CatalogSnapshot snapshot, Long semesterId, OfferingUpsertRequest request,
                                Long excludeOfferingId) {
        Professor professor = professorRepo.findById(request.professorId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "教授不存在"));
        if (snapshot == null || !snapshot.coursesQualifiedFor(professor.getProfessorNo()).contains(request.courseNo())) {
            throw new BusinessException(ErrorCodes.STATE_ERROR,
                    "教授【" + professor.getName() + "】不具备课程【" + request.courseNo() + "】的授课资格");
        }
        for (CourseOffering other : offeringRepo
                .findBySemesterIdAndProfessorIdOrderByIdAsc(semesterId, professor.getId())) {
            if (excludeOfferingId != null && other.getId().equals(excludeOfferingId)) {
                continue;
            }
            if (other.getStatus() == OfferingStatus.CANCELLED) {
                continue;
            }
            if (Objects.equals(other.getDayOfWeek(), request.dayOfWeek())
                    && request.startTime().isBefore(other.getEndTime())
                    && other.getStartTime().isBefore(request.endTime())) {
                throw new BusinessException(ErrorCodes.TIME_CONFLICT,
                        "与教授【" + professor.getName() + "】已排课程时间冲突（"
                                + ViewAssembler.dayText(other.getDayOfWeek()) + " "
                                + other.getStartTime() + "-" + other.getEndTime() + "）");
            }
        }
    }

    private Semester requireSemester(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodes.VALIDATION, "请先选择学期");
        }
        return semesterRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "学期不存在"));
    }

    private CourseOffering requireOffering(Long id) {
        return offeringRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "课程班不存在"));
    }

    private CatalogSnapshot safeSnapshot() {
        try {
            return catalogService.getCatalog();
        } catch (RuntimeException e) {
            return null;
        }
    }
}