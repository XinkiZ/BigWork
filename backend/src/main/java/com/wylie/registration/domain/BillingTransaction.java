package com.wylie.registration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "billing_transaction")
@Getter
@Setter
public class BillingTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(nullable = false)
    private Long semesterId;

    /** 正数=应收，负数=退费（调整期差额） */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingTxType txType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingStatus status = BillingStatus.PENDING;

    @Column(nullable = false)
    private Integer retryCount = 0;

    private LocalDateTime lastAttemptAt;

    private LocalDateTime createdAt;

    private String description;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}