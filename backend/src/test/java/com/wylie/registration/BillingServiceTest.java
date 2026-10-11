package com.wylie.registration;

import com.wylie.registration.domain.BillingStatus;
import com.wylie.registration.domain.BillingTransaction;
import com.wylie.registration.domain.BillingTxType;
import com.wylie.registration.repo.BillingTransactionRepository;
import com.wylie.registration.service.BillingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 计费交易状态机与失败重试测试 */
class BillingServiceTest extends AbstractIntegrationTest {

    @Autowired
    private BillingService billingService;

    @Autowired
    private BillingTransactionRepository txRepo;

    @Test
    @DisplayName("学费计算：单价 500 元/学分")
    void tuitionCalculation() {
        assertEquals(0, billingService.tuitionFor(3).compareTo(new BigDecimal("1500")));
        assertEquals(0, billingService.tuitionFor(10).compareTo(new BigDecimal("5000")));
    }

    @Test
    @DisplayName("计费正常：交易由 PENDING 变为 SENT")
    void successfulSend() {
        BillingTransaction tx = billingService.create(BillingTxType.INITIAL, 1L, 2L,
                new BigDecimal("1500"), "测试账单");

        assertEquals(BillingStatus.PENDING, tx.getStatus());
        assertTrue(billingService.attemptSend(tx.getId()));
        assertEquals(BillingStatus.SENT, txRepo.findById(tx.getId()).orElseThrow().getStatus());
        assertEquals(0, txRepo.findById(tx.getId()).orElseThrow().getRetryCount());
    }

    @Test
    @DisplayName("计费系统不可用：交易标记 FAILED 并累计重试次数，恢复后自动发送成功")
    void failedThenRetried() {
        billingProperties.setAvailable(false);
        BillingTransaction tx = billingService.create(BillingTxType.ADJUSTMENT, 1L, 2L,
                new BigDecimal("500"), "调整期差额");
        assertFalse(billingService.attemptSend(tx.getId()));

        BillingTransaction failed = txRepo.findById(tx.getId()).orElseThrow();
        assertEquals(BillingStatus.FAILED, failed.getStatus());
        assertEquals(1, failed.getRetryCount());
        assertNotNull(failed.getLastAttemptAt(), "应记录最后一次尝试时间");

        // 计费系统恢复后，一次重试周期即发送成功
        billingProperties.setAvailable(true);
        assertEquals(1, billingService.retryNow());
        BillingTransaction sent = txRepo.findById(tx.getId()).orElseThrow();
        assertEquals(BillingStatus.SENT, sent.getStatus());
        assertEquals(1, sent.getRetryCount(), "重试次数保留失败历史");
    }

    @Test
    @DisplayName("定时重试扫描：无待处理交易时不产生任何变更")
    void scheduledRetryIsIdleWithoutPending() {
        billingService.scheduledRetry();
        assertEquals(0, billingService.flushPending());
    }
}