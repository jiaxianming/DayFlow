package com.dayflow.pojo.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录出参
 *
 * @author jiaxianming
 */
@Data
@Builder
public class LoginVO {

    /**
     * JWT token（后续请求放入 Authorization: Bearer &lt;token&gt;）
     */
    private String token;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名（登录名）
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;
}
