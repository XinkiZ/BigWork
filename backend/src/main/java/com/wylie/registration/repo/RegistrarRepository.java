package com.wylie.registration.repo;

import com.wylie.registration.domain.Registrar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegistrarRepository extends JpaRepository<Registrar, Long> {

    Optional<Registrar> findByUsername(String username);
}