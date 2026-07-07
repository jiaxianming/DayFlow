package com.dayflow.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author jiaxianming
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 状态码
     */
    private final ResultCode resultCode;

    /**
     * 按状态码构造业务异常
     *
     * @param resultCode 状态码
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    /**
     * 按状态码 + 自定义信息构造
     *
     * @param resultCode 状态码
     * @param message 自定义信息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
