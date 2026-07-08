package com.dayflow.service;

import com.dayflow.pojo.dto.LoginDTO;
import com.dayflow.pojo.vo.LoginVO;

/**
 * 用户鉴权服务
 *
 * @author jiaxianming
 */
public interface UserAuthService {

    /**
     * 登录：校验用户名密码，签发 JWT
     *
     * @param dto 登录入参
     * @return 登录出参（含 token 与用户基本信息）
     */
    LoginVO login(LoginDTO dto);
}
