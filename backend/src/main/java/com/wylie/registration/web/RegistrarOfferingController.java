package com.wylie.registration.web;

import com.wylie.registration.service.OfferingAdminService;
import com.wylie.registration.web.dto.CourseOfferingView;
import com.wylie.registration.web.dto.LegacyCourseView;
import com.wylie.registration.web.dto.OfferingUpsertRequest;
import com.wylie.registration.web.dto.SimpleMessage;
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

/** 注册管理员：课程班（开课）管理与遗留课程目录查询 */
@RestController
@RequestMapping("/api/registrar")
@PreAuthorize("hasRole('REGISTRAR')")
public class RegistrarOfferingController {

    private final OfferingAdminService offeringAdminService;

    public RegistrarOfferingController(OfferingAdminService offeringAdminService) {
        this.offeringAdminService = offeringAdminService;
    }

    /** 指定学期的课程班列表 */
    @GetMapping("/offerings")
    public List<CourseOfferingView> offerings(@RequestParam Long semesterId) {
        return offeringAdminService.list(semesterId);
    }

    /** 新建课程班（课程来自遗留目录，只读引用） */
    @PostMapping("/offerings")
    public CourseOfferingView create(@Valid @RequestBody OfferingUpsertRequest request) {
        return offeringAdminService.create(request);
    }

    /** 编辑课程班（时间/教室/容量/最少人数/授课教授；学期与课程号不可变更） */
    @PutMapping("/offerings/{id}")
    public CourseOfferingView update(@PathVariable Long id, @Valid @RequestBody OfferingUpsertRequest request) {
        return offeringAdminService.update(id, request);
    }

    /** 删除课程班（仅当无选课/课表/成绩引用） */
    @DeleteMapping("/offerings/{id}")
    public SimpleMessage delete(@PathVariable Long id) {
        offeringAdminService.delete(id);
        return SimpleMessage.ok("课程班已删除");
    }

    /** 遗留课程目录列表（开班下拉；目录不可用时返回 503） */
    @GetMapping("/courses")
    public List<LegacyCourseView> courses() {
        return offeringAdminService.legacyCourses();
    }
}