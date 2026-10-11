package com.wylie.registration;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.repo.SemesterRepository;
import com.wylie.registration.service.SemesterService;
import com.wylie.registration.web.dto.SemesterUpsertRequest;
import com.wylie.registration.web.dto.SemesterView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 学期管理测试：新建/编辑校验、设为当前学期唯一性、重新开启注册 */
class SemesterServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SemesterService semesterService;

    @Autowired
    private SemesterRepository semesterRepo;

    private BusinessException expectFailure(String code, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(code, ex.getCode(), "错误码不符，实际信息：" + ex.getMessage());
        return ex;
    }

    private SemesterUpsertRequest request(String code, String name, LocalDate start, LocalDate end,
                                          LocalDate adjustmentStart, LocalDate adjustmentEnd, String status) {
        return new SemesterUpsertRequest(code, name, start, end, adjustmentStart, adjustmentEnd, status);
    }

    @Test
    @DisplayName("学期管理：新建学期成功并默认注册开放中")
    void createDefaultsToOpen() {
        SemesterView view = semesterService.create(request("2099-2100-1", "测试学期",
                LocalDate.of(2099, 9, 1), LocalDate.of(2100, 1, 10), null, null, null));

        assertNotNull(view.id());
        assertEquals("OPEN", view.registrationStatus());
        assertFalse(view.currentFlag());
        assertTrue(semesterRepo.findByCode("2099-2100-1").isPresent());
    }

    @Test
    @DisplayName("学期管理：学期代码重复时提示错误")
    void duplicateCodeRejected() {
        expectFailure(ErrorCodes.VALIDATION, () -> semesterService.create(request("2026-2027-1", "重复代码",
                LocalDate.of(2099, 9, 1), LocalDate.of(2100, 1, 10), null, null, "OPEN")));
    }

    @Test
    @DisplayName("学期管理：开始日期晚于结束日期时提示错误")
    void startAfterEndRejected() {
        expectFailure(ErrorCodes.VALIDATION, () -> semesterService.create(request("2099-2100-2", "非法日期",
                LocalDate.of(2100, 1, 10), LocalDate.of(2099, 9, 1), null, null, "OPEN")));
    }

    @Test
    @DisplayName("学期管理：调整期必须成对填写且位于学期起止之内")
    void adjustmentWindowValidated() {
        expectFailure(ErrorCodes.VALIDATION, () -> semesterService.create(request("2099-2100-3", "调整期缺一",
                LocalDate.of(2099, 9, 1), LocalDate.of(2100, 1, 10), LocalDate.of(2099, 10, 1), null, "OPEN")));
        expectFailure(ErrorCodes.VALIDATION, () -> semesterService.create(request("2099-2100-4", "调整期越界",
                LocalDate.of(2099, 9, 1), LocalDate.of(2100, 1, 10),
                LocalDate.of(2099, 8, 1), LocalDate.of(2099, 12, 1), "OPEN")));
    }

    @Test
    @DisplayName("学期管理：设为当前学期后原当前学期标记被清除（唯一）")
    void setCurrentIsExclusive() {
        semesterService.setCurrent(1L);

        assertTrue(semesterRepo.findById(1L).orElseThrow().getCurrentFlag());
        assertFalse(semesterRepo.findById(2L).orElseThrow().getCurrentFlag());
        assertEquals(1L, semesterService.listAll().stream().filter(SemesterView::currentFlag).count());
    }

    @Test
    @DisplayName("学期管理：重新开启注册（CLOSED 改回 OPEN）")
    void reopenRegistration() {
        SemesterView view = semesterService.update(1L, request("2025-2026-2", "2025-2026学年第二学期",
                LocalDate.of(2026, 2, 23), LocalDate.of(2026, 7, 3),
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 13), "OPEN"));
        assertEquals("OPEN", view.registrationStatus());
    }

    @Test
    @DisplayName("学期管理：编辑不存在的学期返回 NOT_FOUND")
    void updateMissingSemester() {
        expectFailure(ErrorCodes.NOT_FOUND, () -> semesterService.update(999L, request("2099-2100-5", "不存在",
                LocalDate.of(2099, 9, 1), LocalDate.of(2100, 1, 10), null, null, "OPEN")));
    }

    @Test
    @DisplayName("学期管理：列表按 id 升序返回全部学期")
    void listAllOrdered() {
        List<SemesterView> list = semesterService.listAll();
        assertTrue(list.size() >= 2);
        assertTrue(list.get(0).id() < list.get(1).id());
    }
}