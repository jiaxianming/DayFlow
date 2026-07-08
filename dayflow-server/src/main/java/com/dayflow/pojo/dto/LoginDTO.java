package com.dayflow.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录入参
 *
 * @author jiaxianming
 */
@Data
public class LoginDTO {

    /**
     * 用户名（登录名）
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码（明文，由后端与 BCrypt hash 比对）
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
