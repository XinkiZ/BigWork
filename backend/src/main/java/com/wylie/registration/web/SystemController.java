package com.wylie.registration.web;

import com.wylie.registration.service.SystemControlService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统演示控制接口。
 * GET 状态查询公开；切换类操作仅注册管理员可用。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemControlService systemControlService;

    public SystemController(SystemControlService systemControlService) {
        this.systemControlService = systemControlService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return systemControlService.status();
    }

    @PostMapping("/legacy-mode")
    @PreAuthorize("hasRole('REGISTRAR')")
    public Map<String, Object> legacyMode(@RequestParam String mode) {
        return systemControlService.setLegacyMode(mode);
    }

    @PostMapping("/legacy-availability")
    @PreAuthorize("hasRole('REGISTRAR')")
    public Map<String, Object> legacyAvailability(@RequestParam boolean available) {
        return systemControlService.setLegacyAvailable(available);
    }

    @PostMapping("/cache/refresh")
    @PreAuthorize("hasRole('REGISTRAR')")
    public Map<String, Object> refreshCache() {
        return systemControlService.refreshCache();
    }

    @PostMapping("/billing-availability")
    @PreAuthorize("hasRole('REGISTRAR')")
    public Map<String, Object> billingAvailability(@RequestParam boolean available) {
        return systemControlService.setBillingAvailable(available);
    }
}