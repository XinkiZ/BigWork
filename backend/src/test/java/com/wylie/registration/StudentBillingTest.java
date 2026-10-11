package com.wylie.registration;

import com.wylie.registration.domain.BillingStatus;
import com.wylie.registration.domain.BillingTransaction;
import com.wylie.registration.domain.BillingTxType;
import com.wylie.registration.repo.BillingTransactionRepository;
import com.wylie.registration.service.BillingService;
import com.wylie.registration.web.dto.BillingTransactionView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 学生账单（我的计费交易）测试：仅本人可见、可按学期筛选 */
class StudentBillingTest extends AbstractIntegrationTest {

    @Autowired
    private BillingService billingService;

    @Autowired
    private BillingTransactionRepository txRepo;

    private BillingTransaction tx(Long studentId, Long semesterId, String amount,
                                  BillingTxType type, BillingStatus status) {
        BillingTransaction transaction = new BillingTransaction();
        transaction.setStudentId(studentId);
        transaction.setSemesterId(semesterId);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setTxType(type);
        transaction.setStatus(status);
        return txRepo.save(transaction);
    }

    @Test
    @DisplayName("我的账单：仅返回本人交易，按 id 倒序")
    void onlyOwnTransactionsReturned() {
        tx(1L, 2L, "500.00", BillingTxType.INITIAL, BillingStatus.PENDING);
        BillingTransaction adjustment = tx(1L, 1L, "-250.00", BillingTxType.ADJUSTMENT, BillingStatus.SENT);
        tx(2L, 2L, "300.00", BillingTxType.INITIAL, BillingStatus.FAILED);

        List<BillingTransactionView> views = billingService.listForStudent(1L, null);

        assertEquals(2, views.size(), "学生 2 的账单不可见");
        assertTrue(views.stream().allMatch(v -> "2026001".equals(v.studentNo())));
        assertEquals(adjustment.getId(), views.get(0).id(), "按 id 倒序（最新在前）");
        assertEquals(new BigDecimal("-250.00"), views.get(0).amount());
        assertEquals("ADJUSTMENT", views.get(0).txType());
        assertEquals("SENT", views.get(0).status());
    }

    @Test
    @DisplayName("我的账单：按学期筛选")
    void filterBySemester() {
        tx(1L, 2L, "500.00", BillingTxType.INITIAL, BillingStatus.PENDING);
        tx(1L, 1L, "-250.00", BillingTxType.ADJUSTMENT, BillingStatus.SENT);

        List<BillingTransactionView> semester2 = billingService.listForStudent(1L, 2L);
        assertEquals(1, semester2.size());
        assertEquals(new BigDecimal("500.00"), semester2.get(0).amount());
        assertEquals("2026-2027学年第一学期", semester2.get(0).semesterName(), "账单应带学期名称");
    }

    @Test
    @DisplayName("我的账单：接口仅返回本人数据且支持学期参数")
    void billingEndpointReturnsOwnData() throws Exception {
        tx(1L, 2L, "500.00", BillingTxType.INITIAL, BillingStatus.PENDING);
        tx(1L, 1L, "-250.00", BillingTxType.ADJUSTMENT, BillingStatus.SENT);
        tx(2L, 2L, "300.00", BillingTxType.INITIAL, BillingStatus.FAILED);

        mockMvc.perform(get("/api/student/billing-transactions")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].studentNo").value("2026001"))
                .andExpect(jsonPath("$[1].studentNo").value("2026001"));

        mockMvc.perform(get("/api/student/billing-transactions").param("semesterId", "2")
                        .header("Authorization", "Bearer " + studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(500.00));
    }

    @Test
    @DisplayName("我的账单：无交易时返回空列表")
    void emptyWhenNoTransactions() {
        assertTrue(billingService.listForStudent(3L, null).isEmpty());
    }
}