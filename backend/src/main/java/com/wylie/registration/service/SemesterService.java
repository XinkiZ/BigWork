package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.RegistrationStatus;
import com.wylie.registration.domain.Semester;
import com.wylie.registration.repo.SemesterRepository;
import com.wylie.registration.web.dto.SemesterUpsertRequest;
import com.wylie.registration.web.dto.SemesterView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 学期管理：列表、新建/编辑学期、设为当前学期（重新开启注册 = 编辑 registrationStatus 为 OPEN） */
@Service
public class SemesterService {

    private final SemesterRepository semesterRepo;

    public SemesterService(SemesterRepository semesterRepo) {
        this.semesterRepo = semesterRepo;
    }

    public List<SemesterView> listAll() {
        return semesterRepo.findAllByOrderByIdAsc().stream().map(SemesterService::toView).toList();
    }

    @Transactional
    public SemesterView create(SemesterUpsertRequest request) {
        validate(request);
        if (semesterRepo.findByCode(request.code().trim()).isPresent()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "学期代码已存在：" + request.code().trim());
        }
        Semester semester = new Semester();
        apply(semester, request);
        semester.setCurrentFlag(false);
        semester.setRegistrationStatus(parseStatus(request.registrationStatus(), RegistrationStatus.OPEN));
        semesterRepo.save(semester);
        return toView(semester);
    }

    @Transactional
    public SemesterView update(Long id, SemesterUpsertRequest request) {
        Semester semester = require(id);
        validate(request);
        semesterRepo.findByCode(request.code().trim()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BusinessException(ErrorCodes.VALIDATION, "学期代码已存在：" + request.code().trim());
            }
        });
        apply(semester, request);
        if (request.registrationStatus() != null && !request.registrationStatus().isBlank()) {
            semester.setRegistrationStatus(parseStatus(request.registrationStatus(), semester.getRegistrationStatus()));
        }
        semesterRepo.save(semester);
        return toView(semester);
    }

    @Transactional
    public SemesterView setCurrent(Long id) {
        Semester target = require(id);
        for (Semester semester : semesterRepo.findAll()) {
            boolean shouldBeCurrent = semester.getId().equals(id);
            if (!Objects.equals(semester.getCurrentFlag(), shouldBeCurrent)) {
                semester.setCurrentFlag(shouldBeCurrent);
                semesterRepo.save(semester);
            }
        }
        return toView(target);
    }

    private Semester require(Long id) {
        return semesterRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "学期不存在"));
    }

    private void apply(Semester semester, SemesterUpsertRequest request) {
        semester.setCode(request.code().trim());
        semester.setName(request.name().trim());
        semester.setStartDate(request.startDate());
        semester.setEndDate(request.endDate());
        semester.setAdjustmentStart(request.adjustmentStart());
        semester.setAdjustmentEnd(request.adjustmentEnd());
    }

    private void validate(SemesterUpsertRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "开始日期不能晚于结束日期");
        }
        boolean hasStart = request.adjustmentStart() != null;
        boolean hasEnd = request.adjustmentEnd() != null;
        if (hasStart != hasEnd) {
            throw new BusinessException(ErrorCodes.VALIDATION, "选课调整期开始与结束日期需同时填写");
        }
        if (hasStart) {
            if (request.adjustmentStart().isAfter(request.adjustmentEnd())) {
                throw new BusinessException(ErrorCodes.VALIDATION, "调整期开始日期不能晚于结束日期");
            }
            if (request.adjustmentStart().isBefore(request.startDate())
                    || request.adjustmentEnd().isAfter(request.endDate())) {
                throw new BusinessException(ErrorCodes.VALIDATION, "选课调整期必须位于学期起止日期之内");
            }
        }
    }

    private RegistrationStatus parseStatus(String value, RegistrationStatus fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return RegistrationStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCodes.VALIDATION, "注册状态仅支持 OPEN 或 CLOSED");
        }
    }

    public static SemesterView toView(Semester semester) {
        return new SemesterView(semester.getId(), semester.getCode(), semester.getName(),
                semester.getStartDate(), semester.getEndDate(), semester.getRegistrationStatus().name(),
                semester.getAdjustmentStart(), semester.getAdjustmentEnd(),
                Boolean.TRUE.equals(semester.getCurrentFlag()));
    }
}