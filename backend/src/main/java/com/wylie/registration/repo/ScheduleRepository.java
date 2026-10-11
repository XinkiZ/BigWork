package com.wylie.registration.repo;

import com.wylie.registration.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByStudentIdAndSemesterId(Long studentId, Long semesterId);

    List<Schedule> findBySemesterId(Long semesterId);
}