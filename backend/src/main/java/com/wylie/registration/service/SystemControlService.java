package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.config.BillingProperties;
import com.wylie.registration.config.LegacyProperties;
import com.wylie.registration.domain.Semester;
import com.wylie.registration.legacy.CatalogService;
import com.wylie.registration.repo.SemesterRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 系统演示控制：遗留目录开关、缓存刷新、计费可用性开关 */
@Service
public class SystemControlService {

    private final LegacyProperties legacyProps;
    private final BillingProperties billingProps;
    private final CatalogService catalogService;
    private final SemesterRepository semesterRepo;

    public SystemControlService(LegacyProperties legacyProps, BillingProperties billingProps,
                                CatalogService catalogService, SemesterRepository semesterRepo) {
        this.legacyProps = legacyProps;
        this.billingProps = billingProps;
        this.catalogService = catalogService;
        this.semesterRepo = semesterRepo;
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("legacy", catalogService.status());
        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("available", billingProps.isAvailable());
        billing.put("retryIntervalSeconds", billingProps.getRetryIntervalSeconds());
        result.put("billing", billing);
        Semester current = semesterRepo.findByCurrentFlagTrue().orElse(null);
        if (current != null) {
            Map<String, Object> sem = new LinkedHashMap<>();
            sem.put("id", current.getId());
            sem.put("name", current.getName());
            sem.put("registrationStatus", current.getRegistrationStatus().name());
            sem.put("adjustmentStart", String.valueOf(current.getAdjustmentStart()));
            sem.put("adjustmentEnd", String.valueOf(current.getAdjustmentEnd()));
            result.put("semester", sem);
        }
        return result;
    }

    public Map<String, Object> setLegacyMode(String mode) {
        if (!"direct".equalsIgnoreCase(mode) && !"cached".equalsIgnoreCase(mode)) {
            throw new BusinessException(ErrorCodes.VALIDATION, "模式仅支持 direct 或 cached");
        }
        legacyProps.setMode(mode.toLowerCase());
        return catalogService.status();
    }

    public Map<String, Object> setLegacyAvailable(boolean available) {
        legacyProps.setAvailable(available);
        return catalogService.status();
    }

    public Map<String, Object> refreshCache() {
        catalogService.refresh();
        return catalogService.status();
    }

    public Map<String, Object> setBillingAvailable(boolean available) {
        billingProps.setAvailable(available);
        return status();
    }
}