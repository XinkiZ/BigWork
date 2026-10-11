package com.wylie.registration.service;

import com.wylie.registration.domain.CourseOffering;
import com.wylie.registration.domain.EnrollmentStatus;
import com.wylie.registration.domain.Professor;
import com.wylie.registration.legacy.CatalogSnapshot;
import com.wylie.registration.repo.CourseOfferingRepository;
import com.wylie.registration.repo.EnrollmentRepository;
import com.wylie.registration.repo.ProfessorRepository;
import com.wylie.registration.web.dto.CourseOfferingView;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 课程班视图装配 + 人数统计 */
@Service
public class OfferingQueryService {

    private final CourseOfferingRepository offeringRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final ProfessorRepository professorRepo;
    private final ViewAssembler assembler;

    public OfferingQueryService(CourseOfferingRepository offeringRepo, EnrollmentRepository enrollmentRepo,
                                ProfessorRepository professorRepo, ViewAssembler assembler) {
        this.offeringRepo = offeringRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.professorRepo = professorRepo;
        this.assembler = assembler;
    }

    public long enrolledCount(Long offeringId) {
        return enrollmentRepo.countByOfferingIdAndStatus(offeringId, EnrollmentStatus.REGISTERED);
    }

    public Map<Long, String> professorNames() {
        return professorRepo.findAll().stream()
                .collect(Collectors.toMap(Professor::getId, Professor::getName, (a, b) -> a));
    }

    public List<CourseOfferingView> toViews(List<CourseOffering> offerings, CatalogSnapshot snapshot) {
        return toViews(offerings, snapshot, null);
    }

    /** passedCourses 非空表示学生场景：先修课程标注是否已通过 */
    public List<CourseOfferingView> toViews(List<CourseOffering> offerings, CatalogSnapshot snapshot,
                                            Set<String> passedCourses) {
        Map<Long, String> names = professorNames();
        return offerings.stream()
                .map(o -> assembler.offering(o, enrolledCount(o.getId()),
                        o.getProfessorId() == null ? null : names.get(o.getProfessorId()), snapshot, passedCourses))
                .toList();
    }

    public CourseOfferingView toView(CourseOffering offering, CatalogSnapshot snapshot) {
        return toViews(List.of(offering), snapshot).get(0);
    }

    /** 使用预先查好的教授名映射装配视图（批量场景避免重复查询） */
    public CourseOfferingView toViewWithNames(CourseOffering offering, CatalogSnapshot snapshot, Map<Long, String> professorNames) {
        return toViewWithNames(offering, snapshot, professorNames, null);
    }

    public CourseOfferingView toViewWithNames(CourseOffering offering, CatalogSnapshot snapshot,
                                              Map<Long, String> professorNames, Set<String> passedCourses) {
        return assembler.offering(offering, enrolledCount(offering.getId()),
                offering.getProfessorId() == null ? null : professorNames.get(offering.getProfessorId()),
                snapshot, passedCourses);
    }
}