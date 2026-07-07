package com.dayflow.common;

import lombok.Getter;

import java.io.Serializable;

/**
 * 统一返回包装
 *
 * @param <T> 数据载荷类型
 * @author jiaxianming
 */
@Getter
public class Result<T> implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 提示信息
     */
    private final String message;

    /**
     * 数据载荷
     */
    private final T data;

    /**
     * 全参构造
     *
     * @param code 状态码
     * @param message 提示信息
     * @param data 数据载荷
     */
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功（无数据）
     *
     * @param <T> 载荷类型
     * @return 成功结果
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功（带数据）
     *
     * @param data 数据
     * @param <T> 载荷类型
     * @return 成功结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 失败（按状态码）
     *
     * @param resultCode 状态码枚举
     * @param <T> 载荷类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 失败（自定义码与信息）
     *
     * @param code 状态码
     * @param message 提示信息
     * @param <T> 载荷类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
