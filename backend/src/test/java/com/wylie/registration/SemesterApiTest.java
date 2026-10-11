package com.wylie.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 多学期能力接口测试：学期管理、设为当前学期、重开注册、课程班（开班）管理 */
class SemesterApiTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String semesterBody(String code, String name, String start, String end,
                                String adjStart, String adjEnd, String status) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("startDate", start);
        body.put("endDate", end);
        body.put("adjustmentStart", adjStart);
        body.put("adjustmentEnd", adjEnd);
        body.put("registrationStatus", status);
        return objectMapper.writeValueAsString(body);
    }

    private String offeringBody(Long semesterId, String courseNo, int day, String start, String end,
                                String room, Integer capacity, Integer minStudents, Long professorId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("semesterId", semesterId);
        body.put("courseNo", courseNo);
        body.put("dayOfWeek", day);
        body.put("startTime", start);
        body.put("endTime", end);
        body.put("room", room);
        body.put("capacity", capacity);
        body.put("minStudents", minStudents);
        body.put("professorId", professorId);
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("学期列表：登录即可访问，按 id 升序返回全部学期")
    void semesterListAvailableToAllRoles() throws Exception {
        mockMvc.perform(get("/api/semesters").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("2025-2026-2"))
                .andExpect(jsonPath("$[0].currentFlag").value(false))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].registrationStatus").value("OPEN"))
                .andExpect(jsonPath("$[1].currentFlag").value(true))
                .andExpect(jsonPath("$[2].code").value("2026-2027-2"));

        mockMvc.perform(get("/api/semesters").header("Authorization", "Bearer " + professorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("学期列表：未携带令牌返回 401")
    void semesterListRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/semesters"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("学期管理：学生调用返回 403")
    void studentCannotManageSemesters() throws Exception {
        mockMvc.perform(post("/api/registrar/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semesterBody("2027-2028-1", "新学年", "2027-09-06", "2028-01-14", null, null, null))
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("新建学期：默认注册开放中且非当前学期")
    void createSemester() throws Exception {
        mockMvc.perform(post("/api/registrar/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semesterBody("2027-2028-1", "2027-2028学年第一学期",
                                "2027-09-06", "2028-01-14", "2027-09-20", "2028-01-08", null))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2027-2028-1"))
                .andExpect(jsonPath("$.registrationStatus").value("OPEN"))
                .andExpect(jsonPath("$.currentFlag").value(false));

        mockMvc.perform(get("/api/semesters").header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @DisplayName("新建学期：代码重复返回 400")
    void duplicateSemesterCodeRejected() throws Exception {
        mockMvc.perform(post("/api/registrar/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semesterBody("2026-2027-1", "重复代码", "2026-09-07", "2027-01-15", null, null, null))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    @DisplayName("新建学期：起止日期与调整期校验")
    void semesterDateValidation() throws Exception {
        mockMvc.perform(post("/api/registrar/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semesterBody("2027-2028-1", "倒置日期", "2028-01-14", "2027-09-06", null, null, null))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        mockMvc.perform(post("/api/registrar/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semesterBody("2027-2028-1", "调整期越界", "2027-09-06", "2028-01-14",
                                "2027-08-01", "2027-09-30", null))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    @DisplayName("编辑学期：重新开启注册（CLOSED 改回 OPEN）")
    void reopenRegistrationByUpdate() throws Exception {
        mockMvc.perform(put("/api/registrar/semesters/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semesterBody("2025-2026-2", "2025-2026学年第二学期",
                                "2026-02-23", "2026-07-03", "2026-03-02", "2026-03-13", "OPEN"))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationStatus").value("OPEN"));
    }

    @Test
    @DisplayName("编辑学期：学期不存在返回 404")
    void updateUnknownSemesterRejected() throws Exception {
        mockMvc.perform(put("/api/registrar/semesters/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(semesterBody("2099-1", "不存在", "2099-01-01", "2099-06-01", null, null, null))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("设为当前学期：任意学期均可设为当前且唯一")
    void setCurrentSemester() throws Exception {
        mockMvc.perform(post("/api/registrar/semesters/1/set-current")
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.currentFlag").value(true));

        mockMvc.perform(get("/api/semesters").header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentFlag").value(true))
                .andExpect(jsonPath("$[1].currentFlag").value(false))
                .andExpect(jsonPath("$[2].currentFlag").value(false));
    }

    @Test
    @DisplayName("课程班列表：按学期查询，学生调用返回 403")
    void offeringListBySemester() throws Exception {
        mockMvc.perform(get("/api/registrar/offerings").param("semesterId", "2")
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[0].courseNo").value("CS101"));

        mockMvc.perform(get("/api/registrar/offerings").param("semesterId", "2")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("开班：自动填充课程信息，新建后为 PROPOSED 且可删除")
    void createAndDeleteOffering() throws Exception {
        String created = mockMvc.perform(post("/api/registrar/offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringBody(2L, "CS100", 7, "16:00:00", "17:40:00", "A101", 10, 3, null))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseNo").value("CS100"))
                .andExpect(jsonPath("$.title").value("计算机导论"))
                .andExpect(jsonPath("$.credits").value(1))
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.enrolledCount").value(0))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(created).get("offeringId").asLong();
        mockMvc.perform(delete("/api/registrar/offerings/" + id)
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/registrar/offerings").param("semesterId", "2")
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11));
    }

    @Test
    @DisplayName("开班：目录外课程号与已结束学期均被拒绝")
    void createOfferingValidation() throws Exception {
        mockMvc.perform(post("/api/registrar/offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringBody(2L, "XX999", 7, "16:00:00", "17:40:00", "A101", 10, 3, null))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        mockMvc.perform(post("/api/registrar/offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringBody(1L, "CS100", 7, "16:00:00", "17:40:00", "A101", 10, 3, null))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    @DisplayName("开班：容量越界与教授资格不符被拒绝")
    void createOfferingCapacityAndProfessorValidation() throws Exception {
        mockMvc.perform(post("/api/registrar/offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringBody(2L, "CS100", 7, "16:00:00", "17:40:00", "A101", 11, 3, null))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));

        mockMvc.perform(post("/api/registrar/offerings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offeringBody(2L, "EN201", 7, "16:00:00", "17:40:00", "A101", 10, 3, 3L))
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_ERROR"));
    }

    @Test
    @DisplayName("删除课程班：存在选课记录时拒绝")
    void deleteOfferingWithReferencesRejected() throws Exception {
        mockMvc.perform(delete("/api/registrar/offerings/1")
                        .header("Authorization", "Bearer " + registrarToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_ERROR"));
    }

    @Test
    @DisplayName("教授授课列表：按学期查询，旧接口已移除")
    void professorOfferingsBySemester() throws Exception {
        mockMvc.perform(get("/api/professor/offerings").param("semesterId", "1")
                        .header("Authorization", "Bearer " + professorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/professor/offerings")
                        .header("Authorization", "Bearer " + professorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/professor/offerings/previous")
                        .header("Authorization", "Bearer " + professorToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("历史学期课表：无选课计划行时回退为该学期已注册课程（只读）")
    void historicalScheduleFallsBackToEnrollments() throws Exception {
        mockMvc.perform(get("/api/student/schedule").param("semesterId", "1")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HISTORY"))
                .andExpect(jsonPath("$.semesterId").value(1))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].status").value("REGISTERED"))
                .andExpect(jsonPath("$.items[0].itemType").value("PRIMARY"))
                .andExpect(jsonPath("$.items[0].offering.courseNo").value("CS101"))
                .andExpect(jsonPath("$.items[1].offering.courseNo").value("CS201"))
                .andExpect(jsonPath("$.items[2].offering.courseNo").value("EN101"));

        mockMvc.perform(get("/api/student/schedule")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.items.length()").value(6));
    }
}