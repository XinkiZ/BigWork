package com.wylie.registration.repo;

import com.wylie.registration.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    List<Grade> findByStudentId(Long studentId);

    List<Grade> findByOfferingId(Long offeringId);

    Optional<Grade> findByOfferingIdAndStudentId(Long offeringId, Long studentId);

    boolean existsByStudentId(Long studentId);
}