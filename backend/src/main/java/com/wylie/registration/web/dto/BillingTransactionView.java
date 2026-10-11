package com.wylie.registration.web.dto;

import java.math.BigDecimal;

public record BillingTransactionView(Long id, String studentNo, String studentName, String semesterName,
                                     BigDecimal amount, String txType, String status, int retryCount,
                                     String lastAttemptAt, String description) {
}