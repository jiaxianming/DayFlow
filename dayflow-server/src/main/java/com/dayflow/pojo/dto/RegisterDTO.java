package com.dayflow.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册入参
 *
 * @author jiaxianming
 */
@Data
public class RegisterDTO {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
