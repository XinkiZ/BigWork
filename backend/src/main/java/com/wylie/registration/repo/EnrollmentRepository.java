package com.wylie.registration.repo;

import com.wylie.registration.domain.Enrollment;
import com.wylie.registration.domain.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByOfferingIdAndStatus(Long offeringId, EnrollmentStatus status);

    Optional<Enrollment> findByOfferingIdAndStudentId(Long offeringId, Long studentId);

    List<Enrollment> findByOfferingIdAndStatus(Long offeringId, EnrollmentStatus status);

    List<Enrollment> findByStudentIdAndStatus(Long studentId, EnrollmentStatus status);

    boolean existsByStudentIdAndStatus(Long studentId, EnrollmentStatus status);
}