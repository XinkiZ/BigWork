package com.wylie.registration.repo;

import com.wylie.registration.domain.BillingStatus;
import com.wylie.registration.domain.BillingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BillingTransactionRepository extends JpaRepository<BillingTransaction, Long> {

    List<BillingTransaction> findByStatusInOrderByIdAsc(Collection<BillingStatus> statuses);

    List<BillingTransaction> findBySemesterIdOrderByIdAsc(Long semesterId);

    List<BillingTransaction> findAllByOrderByIdDesc();

    List<BillingTransaction> findByStudentIdOrderByIdDesc(Long studentId);

    List<BillingTransaction> findByStudentIdAndSemesterIdOrderByIdDesc(Long studentId, Long semesterId);
}