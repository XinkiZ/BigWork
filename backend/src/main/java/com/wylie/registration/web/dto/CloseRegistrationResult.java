package com.wylie.registration.web.dto;

import java.math.BigDecimal;
import java.util.List;

/** 关闭注册执行报告 */
public record CloseRegistrationResult(
        Long semesterId,
        String semesterName,
        String closedAt,
        List<OfferingBrief> cancelledOfferings,
        int confirmedOfferingCount,
        List<PromotionRecord> promotions,
        List<BackupFailure> backupFailures,
        List<RemovedItem> removedItems,
        int billedStudentCount,
        BigDecimal totalAmount,
        int billingTransactionCount,
        List<String> messages) {

    public record OfferingBrief(Long offeringId, String courseNo, String title, String reason) {
    }

    public record PromotionRecord(String studentNo, String studentName, String courseNo, String title, int order) {
    }

    public record BackupFailure(String studentNo, String studentName, String courseNo, String title, String reason) {
    }

    public record RemovedItem(String studentNo, String studentName, String courseNo, String title, String itemType) {
    }
}