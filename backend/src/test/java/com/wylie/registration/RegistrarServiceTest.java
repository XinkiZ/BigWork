package com.wylie.registration;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.service.AuthService;
import com.wylie.registration.service.RegistrarService;
import com.wylie.registration.web.dto.LoginRequest;
import com.wylie.registration.web.dto.ProfessorUpsertRequest;
import com.wylie.registration.web.dto.StudentDto;
import com.wylie.registration.web.dto.StudentUpsertRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 注册管理员用例测试：维护学生信息、维护教授信息 */
class RegistrarServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RegistrarService registrarService;

    @Autowired
    private AuthService authService;

    private BusinessException expectFailure(String code, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(code, ex.getCode(), "错误码不符，实际信息：" + ex.getMessage());
        return ex;
    }

    @Test
    @DisplayName("维护学生信息：新增学生由系统生成唯一学号并以默认口令可登录")
    void createStudentGeneratesUniqueId() {
        StudentDto dto = registrarService.createStudent(new StudentUpsertRequest(
                "测试学生", LocalDate.of(2006, 1, 1), "310101200601011234", null, null));

        assertEquals("2026017", dto.studentNo(), "应在最大学号基础上 +1 生成唯一学号");
        assertEquals("ACTIVE", dto.status());
        var login = authService.login(new LoginRequest(dto.studentNo(), "123456"));
        assertEquals("STUDENT", login.role());
        assertEquals("测试学生", login.name());
    }

    @Test
    @DisplayName("维护学生信息：学号按最大学号递增，不重复")
    void studentNumbersAreSequential() {
        StudentDto first = registrarService.createStudent(new StudentUpsertRequest(
                "学生甲", null, null, null, null));
        StudentDto second = registrarService.createStudent(new StudentUpsertRequest(
                "学生乙", null, null, null, null));

        assertEquals("2026017", first.studentNo());
        assertEquals("2026018", second.studentNo());
    }

    @Test
    @DisplayName("维护学生信息：ID 不存在时提示错误")
    void unknownStudentIdRejected() {
        expectFailure(ErrorCodes.NOT_FOUND, () -> registrarService.updateStudent(9999L,
                new StudentUpsertRequest("张三", null, null, null, null)));
        expectFailure(ErrorCodes.NOT_FOUND, () -> registrarService.deleteStudent(9999L));
    }

    @Test
    @DisplayName("维护学生信息：更新与删除")
    void updateAndDeleteStudent() {
        var updated = registrarService.updateStudent(1L, new StudentUpsertRequest(
                "陈晨", LocalDate.of(2005, 3, 12), "310101200503121234", "GRADUATED", LocalDate.of(2028, 6, 30)));
        assertEquals("GRADUATED", updated.status());

        // 曹静（2026016）无历史成绩，允许删除（其课表与选课记录级联清理）
        registrarService.deleteStudent(16L);
        assertTrue(registrarService.listStudents(null).stream().noneMatch(s -> s.id().equals(16L)));
    }

    @Test
    @DisplayName("维护学生信息：存在成绩记录的学生不允许删除")
    void studentWithGradesCannotBeDeleted() {
        BusinessException ex = expectFailure(ErrorCodes.STATE_ERROR, () -> registrarService.deleteStudent(1L));
        assertTrue(ex.getMessage().contains("成绩"), "应说明存在成绩记录");
    }

    @Test
    @DisplayName("维护学生信息：按关键字检索")
    void searchStudentsByKeyword() {
        assertEquals(1, registrarService.listStudents("陈晨").size());
        assertTrue(registrarService.listStudents("2026016").stream()
                .anyMatch(s -> s.name().equals("曹静")));
    }

    @Test
    @DisplayName("维护教授信息：新增教授生成唯一编号并可登录")
    void createProfessorGeneratesUniqueId() {
        var dto = registrarService.createProfessor(new ProfessorUpsertRequest(
                "新教授", null, null, null, "计算机学院"));

        assertEquals("P006", dto.professorNo());
        var login = authService.login(new LoginRequest("P006", "123456"));
        assertEquals("PROFESSOR", login.role());
    }

    @Test
    @DisplayName("维护教授信息：更新、检索与删除")
    void updateSearchDeleteProfessor() {
        var updated = registrarService.updateProfessor(5L, new ProfessorUpsertRequest(
                "孙芳", null, null, "RETIRED", "物理学院"));
        assertEquals("RETIRED", updated.status());
        assertTrue(registrarService.listProfessors("孙").stream().anyMatch(p -> p.id().equals(5L)));

        registrarService.deleteProfessor(5L);
        assertTrue(registrarService.listProfessors(null).stream().noneMatch(p -> p.id().equals(5L)));
    }

    @Test
    @DisplayName("维护教授信息：ID 不存在时提示错误")
    void unknownProfessorIdRejected() {
        expectFailure(ErrorCodes.NOT_FOUND, () -> registrarService.updateProfessor(9999L,
                new ProfessorUpsertRequest("李四", null, null, null, null)));
        expectFailure(ErrorCodes.NOT_FOUND, () -> registrarService.deleteProfessor(9999L));
    }
}