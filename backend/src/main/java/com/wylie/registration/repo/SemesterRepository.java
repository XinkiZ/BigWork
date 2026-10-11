package com.wylie.registration.repo;

import com.wylie.registration.domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {

    Optional<Semester> findByCurrentFlagTrue();

    List<Semester> findAllByOrderByIdAsc();

    Optional<Semester> findFirstByIdLessThanOrderByIdDesc(Long id);
}