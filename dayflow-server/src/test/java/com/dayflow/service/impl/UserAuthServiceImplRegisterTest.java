package com.dayflow.service.impl;

import com.dayflow.common.BusinessException;
import com.dayflow.common.JwtUtil;
import com.dayflow.mapper.UserMapper;
import com.dayflow.pojo.dto.RegisterDTO;
import com.dayflow.pojo.entity.UserEntity;
import com.dayflow.pojo.vo.LoginVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserAuthService.register 纯 Mockito 单测
 * <p>验证查重/加密/签 JWT 逻辑；passwordEncoder 为实例字段（final 已初始化），
 * @InjectMocks 时自动 new 真实 BCrypt，故可用真实 BCryptPasswordEncoder 验证加密。</p>
 *
 * @author jiaxianming
 */
@ExtendWith(MockitoExtension.class)
class UserAuthServiceImplRegisterTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserAuthServiceImpl userAuthService;

    /**
     * 正常注册：不重名 → insert 落库（密码已 BCrypt 加密）→ 签 JWT → 返回 LoginVO
     */
    @Test
    void registerSucceeds() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("pass123");

        when(userMapper.selectOne(any())).thenReturn(null);
        when(jwtUtil.generate(any(), eq("alice"))).thenReturn("token-alice");

        LoginVO vo = userAuthService.register(dto);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(captor.capture());
        UserEntity saved = captor.getValue();

        assertEquals("alice", saved.getUsername());
        // 密码已加密：不是明文，且可被 BCrypt 验证匹配
        assertTrue(!"pass123".equals(saved.getPasswordHash()), "落库的不能是明文密码");
        assertTrue(new BCryptPasswordEncoder().matches("pass123", saved.getPasswordHash()),
                "落库 hash 必须能被 BCrypt 验证匹配");
        // 返回的 LoginVO
        assertEquals("token-alice", vo.getToken());
        assertEquals("alice", vo.getUsername());
    }

    /**
     * 用户名已存在：抛 BusinessException(409)，且不执行 insert
     */
    @Test
    void registerThrowsWhenUsernameExists() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("pass123");

        UserEntity existing = new UserEntity();
        existing.setUsername("alice");
        when(userMapper.selectOne(any())).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class, () -> userAuthService.register(dto));
        assertEquals(409, ex.getCode());
        assertNotNull(ex.getMessage());
        verify(userMapper, never()).insert(any(UserEntity.class));
    }
}
