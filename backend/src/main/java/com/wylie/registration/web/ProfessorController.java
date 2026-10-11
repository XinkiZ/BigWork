package com.wylie.registration.web;

import com.wylie.registration.security.AuthUser;
import com.wylie.registration.service.ProfessorService;
import com.wylie.registration.web.dto.CourseOfferingView;
import com.wylie.registration.web.dto.GradeRow;
import com.wylie.registration.web.dto.GradeSaveRequest;
import com.wylie.registration.web.dto.QualifiedCourseView;
import com.wylie.registration.web.dto.RosterRow;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 教授用例接口：选择授课课程、查看选课名单、录入成绩 */
@RestController
@RequestMapping("/api/professor")
@PreAuthorize("hasRole('PROFESSOR')")
public class ProfessorController {

    private final ProfessorService professorService;

    public ProfessorController(ProfessorService professorService) {
        this.professorService = professorService;
    }

    /** 具备授课资格的课程及本学期课程班 */
    @GetMapping("/qualified-courses")
    public List<QualifiedCourseView> qualifiedCourses() {
        return professorService.qualifiedCourses(AuthUser.current().id());
    }

    /** 我在指定学期的授课课程班（不传则当前学期；历史学期用于名单查看与成绩录入） */
    @GetMapping("/offerings")
    public List<CourseOfferingView> myOfferings(@RequestParam(required = false) Long semesterId) {
        return professorService.myOfferings(AuthUser.current().id(), semesterId);
    }

    /** 选择授课课程班 */
    @PostMapping("/offerings/{offeringId}/select")
    public CourseOfferingView select(@PathVariable Long offeringId) {
        return professorService.selectOffering(AuthUser.current().id(), offeringId);
    }

    /** 取消已选授课课程班 */
    @DeleteMapping("/offerings/{offeringId}/select")
    public CourseOfferingView unselect(@PathVariable Long offeringId) {
        return professorService.unselectOffering(AuthUser.current().id(), offeringId);
    }

    /** 查看选课名单 */
    @GetMapping("/offerings/{offeringId}/roster")
    public List<RosterRow> roster(@PathVariable Long offeringId) {
        return professorService.roster(AuthUser.current().id(), offeringId);
    }

    /** 成绩表（上一学期课程班） */
    @GetMapping("/offerings/{offeringId}/grades")
    public List<GradeRow> grades(@PathVariable Long offeringId) {
        return professorService.gradeSheet(AuthUser.current().id(), offeringId);
    }

    /** 录入/修改成绩 */
    @PutMapping("/offerings/{offeringId}/grades")
    public List<GradeRow> saveGrades(@PathVariable Long offeringId,
                                     @Valid @RequestBody GradeSaveRequest request) {
        return professorService.saveGrades(AuthUser.current().id(), offeringId, request);
    }
}