package com.wylie.registration.repo;

import com.wylie.registration.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByStudentNo(String studentNo);

    @Query("select max(cast(s.studentNo as integer)) from Student s")
    Integer findMaxStudentNo();
}