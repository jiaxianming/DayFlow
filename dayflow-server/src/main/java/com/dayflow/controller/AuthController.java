package com.dayflow.controller;

import com.dayflow.common.Result;
import com.dayflow.pojo.dto.LoginDTO;
import com.dayflow.pojo.vo.LoginVO;
import com.dayflow.service.UserAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权接口
 *
 * @author jiaxianming
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserAuthService userAuthService;

    /**
     * 登录：校验用户名密码，签发 JWT
     *
     * @param dto 登录入参
     * @return 登录出参（含 token）
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userAuthService.login(dto));
    }
}
