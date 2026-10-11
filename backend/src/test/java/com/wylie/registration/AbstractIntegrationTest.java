package com.wylie.registration;

import com.wylie.registration.config.BillingProperties;
import com.wylie.registration.config.LegacyProperties;
import com.wylie.registration.domain.UserRole;
import com.wylie.registration.security.AuthUser;
import com.wylie.registration.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集成测试基类。
 *
 * <p>测试库为 bigwork_test（结构与数据由 db/init.sql 复制生成），
 * 每个测试方法运行在独立事务中，结束后自动回滚，保证可重复执行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected LegacyProperties legacyProperties;

    @Autowired
    protected BillingProperties billingProperties;

    /** 每个用例结束后复位演示开关，避免用例之间相互影响 */
    @AfterEach
    void resetDemoSwitches() {
        legacyProperties.setMode("cached");
        legacyProperties.setAvailable(true);
        legacyProperties.setSimulatedLatencyMs(0);
        billingProperties.setAvailable(true);
    }

    /** 直接签发令牌，用于接口鉴权测试 */
    protected String tokenOf(long id, UserRole role, String name, String username) {
        return jwtService.generate(new AuthUser(id, role, name, username));
    }

    protected String studentToken() {
        return tokenOf(1L, UserRole.STUDENT, "陈晨", "2026001");
    }

    protected String professorToken() {
        return tokenOf(1L, UserRole.PROFESSOR, "张伟", "P001");
    }

    protected String registrarToken() {
        return tokenOf(1L, UserRole.REGISTRAR, "教务处管理员", "registrar");
    }
}