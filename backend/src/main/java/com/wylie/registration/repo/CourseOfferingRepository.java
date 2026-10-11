package com.wylie.registration.repo;

import com.wylie.registration.domain.CourseOffering;
import com.wylie.registration.domain.OfferingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    List<CourseOffering> findBySemesterIdOrderByIdAsc(Long semesterId);

    List<CourseOffering> findBySemesterIdAndStatusNotOrderByIdAsc(Long semesterId, OfferingStatus status);

    List<CourseOffering> findBySemesterIdAndProfessorIdOrderByIdAsc(Long semesterId, Long professorId);
}