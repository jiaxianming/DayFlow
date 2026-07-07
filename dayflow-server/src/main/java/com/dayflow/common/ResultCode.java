package com.dayflow.common;

import lombok.Getter;

/**
 * 统一状态码
 *
 * @author jiaxianming
 */
@Getter
public enum ResultCode {

    /**
     * 成功
     */
    SUCCESS(200, "成功"),

    /**
     * 参数错误
     */
    PARAM_ERROR(400, "参数错误"),

    /**
     * 未认证
     */
    UNAUTHORIZED(401, "未认证"),

    /**
     * 业务异常
     */
    BUSINESS_ERROR(500, "业务异常"),

    /**
     * 系统异常
     */
    SYSTEM_ERROR(500, "系统异常");

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 提示信息
     */
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
