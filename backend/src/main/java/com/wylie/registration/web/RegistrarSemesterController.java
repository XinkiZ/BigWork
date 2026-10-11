package com.wylie.registration.web;

import com.wylie.registration.service.SemesterService;
import com.wylie.registration.web.dto.SemesterUpsertRequest;
import com.wylie.registration.web.dto.SemesterView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 注册管理员：学期管理（新建/编辑、设为当前学期；重新开启注册=编辑状态为 OPEN） */
@RestController
@RequestMapping("/api/registrar/semesters")
@PreAuthorize("hasRole('REGISTRAR')")
public class RegistrarSemesterController {

    private final SemesterService semesterService;

    public RegistrarSemesterController(SemesterService semesterService) {
        this.semesterService = semesterService;
    }

    @PostMapping
    public SemesterView create(@Valid @RequestBody SemesterUpsertRequest request) {
        return semesterService.create(request);
    }

    @PutMapping("/{id}")
    public SemesterView update(@PathVariable Long id, @Valid @RequestBody SemesterUpsertRequest request) {
        return semesterService.update(id, request);
    }

    @PostMapping("/{id}/set-current")
    public SemesterView setCurrent(@PathVariable Long id) {
        return semesterService.setCurrent(id);
    }
}