package com.wylie.registration.repo;

import com.wylie.registration.domain.Professor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {

    Optional<Professor> findByProfessorNo(String professorNo);
}