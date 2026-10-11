package com.wylie.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wylie.registration.domain.UserRole;
import com.wylie.registration.web.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 登录与权限（安全需求）接口测试：三种角色登录、鉴权与越权防护 */
class AuthApiTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String loginBody(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginRequest(username, password));
    }

    @Test
    @DisplayName("学生登录成功并返回令牌与当前学期")
    void studentLoginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("2026001", "123456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.name").value("陈晨"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.currentSemesterId").value(2))
                .andExpect(jsonPath("$.registrationStatus").value("OPEN"));
    }

    @Test
    @DisplayName("教授登录成功")
    void professorLoginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("P001", "123456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROFESSOR"))
                .andExpect(jsonPath("$.name").value("张伟"));
    }

    @Test
    @DisplayName("注册管理员登录成功")
    void registrarLoginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("registrar", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REGISTRAR"));
    }

    @Test
    @DisplayName("密码错误返回 401 与统一错误码")
    void wrongPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("2026001", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误，请重新输入"));
    }

    @Test
    @DisplayName("用户不存在返回 401")
    void unknownUserRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("9999999", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("未携带令牌访问业务接口返回 401")
    void anonymousAccessRejected() throws Exception {
        mockMvc.perform(get("/api/student/schedule"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("学生访问管理员接口返回 403（越权防护）")
    void studentCannotAccessRegistrarApi() throws Exception {
        mockMvc.perform(get("/api/registrar/students").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员访问学生接口返回 403（越权防护）")
    void registrarCannotAccessStudentApi() throws Exception {
        mockMvc.perform(get("/api/student/schedule").header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("教授访问学生选课接口返回 403（越权防护）")
    void professorCannotAccessStudentApi() throws Exception {
        mockMvc.perform(get("/api/student/catalog").header("Authorization", "Bearer " + professorToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("学生令牌可正常访问学生接口")
    void studentCanAccessOwnApi() throws Exception {
        mockMvc.perform(get("/api/student/schedule").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesterId").value(2))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("系统状态接口无需登录即可访问（演示面板用）")
    void systemStatusIsPublic() throws Exception {
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legacy.mode").value("cached"))
                .andExpect(jsonPath("$.billing.available").value(true));
    }

    @Test
    @DisplayName("学生无权切换系统演示开关（403）")
    void studentCannotToggleSystemSwitch() throws Exception {
        mockMvc.perform(post("/api/system/legacy-mode").param("mode", "direct")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("访问不存在的接口路径返回 404（而不是 500）")
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/api/registrar/no-such-endpoint")
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("请求方法不支持时返回 405")
    void wrongMethodReturns405() throws Exception {
        mockMvc.perform(put("/api/student/schedule")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("查询参数类型不匹配时返回 400")
    void badParameterTypeReturns400() throws Exception {
        mockMvc.perform(get("/api/student/report-card").param("semesterId", "abc")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    @DisplayName("未携带令牌访问学期列表返回 401")
    void semesterListRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/semesters"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("学生可访问学期列表与学业记录接口")
    void studentCanAccessSemesterListAndTranscript() throws Exception {
        mockMvc.perform(get("/api/semesters").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/student/transcript").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentNo").value("2026001"))
                .andExpect(jsonPath("$.semesters").isArray());
    }

    @Test
    @DisplayName("学生访问学期管理与课程班管理接口返回 403（多学期迭代越权防护）")
    void studentCannotAccessRegistrarSemesterAndOfferingApi() throws Exception {
        mockMvc.perform(post("/api/registrar/semesters")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"2099-1\",\"name\":\"x\",\"startDate\":\"2099-01-01\",\"endDate\":\"2099-06-01\"}")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/registrar/offerings").param("semesterId", "2")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/registrar/courses")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("教授访问学生账单与学业记录接口返回 403（越权防护）")
    void professorCannotAccessStudentBillingApi() throws Exception {
        mockMvc.perform(get("/api/student/billing-transactions")
                        .header("Authorization", "Bearer " + professorToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/student/transcript")
                        .header("Authorization", "Bearer " + professorToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("学生访问教授授课列表接口返回 403（越权防护）")
    void studentCannotAccessProfessorApi() throws Exception {
        mockMvc.perform(get("/api/professor/offerings")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("业务冲突（先修不满足）返回 409 且带统一错误码")
    void businessConflictReturns409() throws Exception {
        // 李思远（学号 2026005）上一学期 CS101 成绩为 F，选 2 号课程班（先修 CS101）应被拒绝
        String failedPrereqToken = tokenOf(5L, UserRole.STUDENT, "李思远", "2026005");
        mockMvc.perform(post("/api/student/schedule/submit")
                        .header("Authorization", "Bearer " + failedPrereqToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"offeringId\":2,\"itemType\":\"PRIMARY\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_NOT_MET"));
    }
}