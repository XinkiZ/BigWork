package com.wylie.registration.web;

import com.wylie.registration.service.BillingService;
import com.wylie.registration.service.CloseRegistrationService;
import com.wylie.registration.service.RegistrarService;
import com.wylie.registration.web.dto.BillingTransactionView;
import com.wylie.registration.web.dto.CloseRegistrationResult;
import com.wylie.registration.web.dto.ProfessorDto;
import com.wylie.registration.web.dto.ProfessorUpsertRequest;
import com.wylie.registration.web.dto.SimpleMessage;
import com.wylie.registration.web.dto.StudentDto;
import com.wylie.registration.web.dto.StudentUpsertRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 注册管理员用例接口：维护学生/教授信息、关闭注册、计费交易监控 */
@RestController
@RequestMapping("/api/registrar")
@PreAuthorize("hasRole('REGISTRAR')")
public class RegistrarController {

    private final RegistrarService registrarService;
    private final CloseRegistrationService closeRegistrationService;
    private final BillingService billingService;

    public RegistrarController(RegistrarService registrarService,
                               CloseRegistrationService closeRegistrationService,
                               BillingService billingService) {
        this.registrarService = registrarService;
        this.closeRegistrationService = closeRegistrationService;
        this.billingService = billingService;
    }

    // ---------------------- 学生信息 ----------------------

    @GetMapping("/students")
    public List<StudentDto> students(@RequestParam(required = false) String keyword) {
        return registrarService.listStudents(keyword);
    }

    @PostMapping("/students")
    public StudentDto createStudent(@Valid @RequestBody StudentUpsertRequest request) {
        return registrarService.createStudent(request);
    }

    @PutMapping("/students/{id}")
    public StudentDto updateStudent(@PathVariable Long id, @Valid @RequestBody StudentUpsertRequest request) {
        return registrarService.updateStudent(id, request);
    }

    @DeleteMapping("/students/{id}")
    public SimpleMessage deleteStudent(@PathVariable Long id) {
        registrarService.deleteStudent(id);
        return SimpleMessage.ok("学生已删除");
    }

    // ---------------------- 教授信息 ----------------------

    @GetMapping("/professors")
    public List<ProfessorDto> professors(@RequestParam(required = false) String keyword) {
        return registrarService.listProfessors(keyword);
    }

    @PostMapping("/professors")
    public ProfessorDto createProfessor(@Valid @RequestBody ProfessorUpsertRequest request) {
        return registrarService.createProfessor(request);
    }

    @PutMapping("/professors/{id}")
    public ProfessorDto updateProfessor(@PathVariable Long id, @Valid @RequestBody ProfessorUpsertRequest request) {
        return registrarService.updateProfessor(id, request);
    }

    @DeleteMapping("/professors/{id}")
    public SimpleMessage deleteProfessor(@PathVariable Long id) {
        registrarService.deleteProfessor(id);
        return SimpleMessage.ok("教授已删除");
    }

    // ---------------------- 关闭注册 ----------------------

    /** 执行关闭注册：确认/取消课程班、备选补位、计算学费并登记计费交易 */
    @PostMapping("/close-registration")
    public Map<String, Object> closeRegistration() {
        CloseRegistrationResult result = closeRegistrationService.closeRegistration();
        int sentNow = billingService.flushPending();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("report", result);
        response.put("billingSentNow", sentNow);
        response.put("billing", billingService.listAll(result.semesterId()));
        return response;
    }

    // ---------------------- 计费交易 ----------------------

    @GetMapping("/billing-transactions")
    public List<BillingTransactionView> billingTransactions(@RequestParam(required = false) Long semesterId) {
        return billingService.listAll(semesterId);
    }

    /** 手动触发重试（等价于一次定时重试周期） */
    @PostMapping("/billing-transactions/retry")
    public Map<String, Object> retryBilling() {
        int sent = billingService.retryNow();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sentNow", sent);
        response.put("billing", billingService.listAll(null));
        return response;
    }
}