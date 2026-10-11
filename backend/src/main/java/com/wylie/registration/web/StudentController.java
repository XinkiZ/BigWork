package com.wylie.registration.web;

import com.wylie.registration.security.AuthUser;
import com.wylie.registration.service.ScheduleService;
import com.wylie.registration.service.StudentQueryService;
import com.wylie.registration.web.dto.CourseOfferingView;
import com.wylie.registration.web.dto.ReportCardView;
import com.wylie.registration.web.dto.ScheduleSubmitRequest;
import com.wylie.registration.web.dto.ScheduleView;
import com.wylie.registration.web.dto.SimpleMessage;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 学生用例接口：课程注册（创建/暂存/更新/删除）+ 查看成绩单 */
@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final ScheduleService scheduleService;
    private final StudentQueryService studentQueryService;

    public StudentController(ScheduleService scheduleService, StudentQueryService studentQueryService) {
        this.scheduleService = scheduleService;
        this.studentQueryService = studentQueryService;
    }

    /** 本学期可选课程目录（来自遗留目录 + 课程班） */
    @GetMapping("/catalog")
    public List<CourseOfferingView> catalog() {
        return scheduleService.catalog(AuthUser.current().id());
    }

    /** 我的课表 */
    @GetMapping("/schedule")
    public ScheduleView schedule() {
        return scheduleService.mySchedule(AuthUser.current().id());
    }

    /** 暂存课表 */
    @PostMapping("/schedule/draft")
    public ScheduleView saveDraft(@Valid @RequestBody ScheduleSubmitRequest request) {
        return scheduleService.saveDraft(AuthUser.current().id(), request);
    }

    /** 提交课表（校验先修/满员/开放状态/时间冲突） */
    @PostMapping("/schedule/submit")
    public ScheduleView submit(@Valid @RequestBody ScheduleSubmitRequest request) {
        return scheduleService.submit(AuthUser.current().id(), request);
    }

    /** 删除课表 */
    @DeleteMapping("/schedule")
    public SimpleMessage deleteSchedule() {
        scheduleService.deleteSchedule(AuthUser.current().id());
        return SimpleMessage.ok("课表已删除，并已从对应课程班移除");
    }

    /** 选课调整期：加课 */
    @PostMapping("/adjustment/add/{offeringId}")
    public ScheduleView adjustmentAdd(@PathVariable Long offeringId) {
        return scheduleService.adjustmentAdd(AuthUser.current().id(), offeringId);
    }

    /** 选课调整期：退课 */
    @PostMapping("/adjustment/drop/{offeringId}")
    public ScheduleView adjustmentDrop(@PathVariable Long offeringId) {
        return scheduleService.adjustmentDrop(AuthUser.current().id(), offeringId);
    }

    /** 查看成绩单（默认上一学期） */
    @GetMapping("/report-card")
    public ReportCardView reportCard(@RequestParam(required = false) Long semesterId) {
        return studentQueryService.reportCard(AuthUser.current().id(), semesterId);
    }
}