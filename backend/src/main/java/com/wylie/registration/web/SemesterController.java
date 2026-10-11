package com.wylie.registration.web;

import com.wylie.registration.service.SemesterService;
import com.wylie.registration.web.dto.SemesterView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 学期列表（全角色可用，用于各页面学期切换下拉） */
@RestController
@RequestMapping("/api/semesters")
@PreAuthorize("isAuthenticated()")
public class SemesterController {

    private final SemesterService semesterService;

    public SemesterController(SemesterService semesterService) {
        this.semesterService = semesterService;
    }

    @GetMapping
    public List<SemesterView> list() {
        return semesterService.listAll();
    }
}