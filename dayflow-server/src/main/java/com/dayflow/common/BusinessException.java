package com.dayflow.common;

import lombok.Getter;

/**
 * 业务异常
 * <p>携带 ResultCode 对应的 code，供 GlobalExceptionHandler 直接回写到 Result.code</p>
 *
 * @author jiaxianming
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 状态码（与 ResultCode.code 对应）
     */
    private final Integer code;

    /**
     * 按状态码构造业务异常
     *
     * @param resultCode 状态码枚举
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 按状态码 + 自定义信息构造
     *
     * @param resultCode 状态码枚举
     * @param message 自定义信息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
