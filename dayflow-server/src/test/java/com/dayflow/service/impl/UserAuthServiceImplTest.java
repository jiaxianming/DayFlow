package com.dayflow.service.impl;

import com.dayflow.common.BusinessException;
import com.dayflow.pojo.dto.LoginDTO;
import com.dayflow.pojo.vo.LoginVO;
import com.dayflow.service.UserAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户鉴权服务集成测试（T5 跨任务校验）
 * <p>真 DB + schema.sql 预置用户：验证 admin/dayflow123 的 BCrypt hash 端到端可登录。
 * 若本测试失败，说明 schema.sql 中预置 hash 与明文密码不匹配（T2 遗留风险）。</p>
 * <p>注入测试用 DeepSeek key 绕过 AiConfig fail-fast（M2 T1 引入）。</p>
 *
 * @author jiaxianming
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-key")
class UserAuthServiceImplTest {

    @Autowired
    private UserAuthService userAuthService;

    /**
     * 正确密码：登录成功，返回非空 token + 正确用户基本信息。
     * 这一条同时验证了 schema.sql 预置 hash ↔ dayflow123 的匹配关系。
     */
    @Test
    void loginWithPresetAdminSucceeds() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("dayflow123");

        LoginVO vo = userAuthService.login(dto);

        assertNotNull(vo);
        assertTrue(vo.getToken() != null && !vo.getToken().isBlank(), "token 不应为空");
        assertEquals(1L, vo.getUserId(), "预置 admin 用户 ID 应为 1");
        assertEquals("admin", vo.getUsername());
        assertEquals("管理员", vo.getNickname());
    }

    /**
     * 错误密码：抛 BusinessException 且 code=401
     */
    @Test
    void loginWithWrongPasswordThrows401() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("wrong");

        BusinessException ex = assertThrows(BusinessException.class, () -> userAuthService.login(dto));
        assertEquals(401, ex.getCode());
    }
}
