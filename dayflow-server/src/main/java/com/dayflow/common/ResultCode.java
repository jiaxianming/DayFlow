package com.dayflow.common;

import lombok.Getter;

/**
 * 统一状态码
 * <p>按 HTTP 语义细化：200 成功 / 400 参数错误 / 401 未认证 / 403 无权限 /
 * 404 资源不存在 / 409 业务规则冲突 / 500 系统异常</p>
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
     * 无权限
     */
    FORBIDDEN(403, "无权限"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 业务规则冲突
     */
    BUSINESS_ERROR(409, "业务规则冲突"),

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
