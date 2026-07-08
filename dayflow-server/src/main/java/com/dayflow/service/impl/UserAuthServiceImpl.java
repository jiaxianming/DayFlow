package com.dayflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayflow.common.BusinessException;
import com.dayflow.common.JwtUtil;
import com.dayflow.common.ResultCode;
import com.dayflow.mapper.UserMapper;
import com.dayflow.pojo.dto.LoginDTO;
import com.dayflow.pojo.entity.UserEntity;
import com.dayflow.pojo.vo.LoginVO;
import com.dayflow.service.UserAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户鉴权服务实现
 * <p>用户名查询 → BCrypt 校验 → 签发 JWT；用户名或密码错误统一回 401（不区分用户是否存在）。</p>
 *
 * @author jiaxianming
 */
@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /**
     * BCrypt 密码编码器（无状态，实例字段复用）
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 登录
     *
     * @param dto 登录入参
     * @return 登录出参
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, dto.getUsername()));
        // 用户不存在或密码不匹配统一回 401，避免泄漏用户是否存在
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }
}
