package com.dayflow.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器
 * <p>异常到 Result.code 的映射：</p>
 * <ul>
 *   <li>BusinessException → 携带 code（通常 409 业务规则冲突）</li>
 *   <li>MethodArgumentNotValidException / BindException → 400 参数错误</li>
 *   <li>HttpMessageNotReadableException → 400 请求体格式错误</li>
 *   <li>NoHandlerFoundException → 404 资源不存在</li>
 *   <li>HttpRequestMethodNotSupportedException → 400 请求方法不支持</li>
 *   <li>兜底 Exception → 500 系统异常</li>
 * </ul>
 *
 * @author jiaxianming
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：直接回写异常携带的 code 与 message
     *
     * @param e 业务异常
     * @return 统一失败结果
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * @RequestBody 校验失败：聚合所有字段错误
     *
     * @param e 校验异常
     * @return 统一失败结果（400）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数错误");
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 表单参数绑定校验失败
     *
     * @param e 绑定异常
     * @return 统一失败结果（400）
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数错误");
        log.warn("参数绑定失败: {}", msg);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 请求体不可读（JSON 格式错误等）
     *
     * @param e 消息转换异常
     * @return 统一失败结果（400）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleBody(HttpMessageNotReadableException e) {
        log.warn("请求体不可读: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求体格式错误");
    }

    /**
     * 找不到匹配的处理器
     *
     * @param e 未找到处理器异常
     * @return 统一失败结果（404）
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNotFound(NoHandlerFoundException e) {
        log.warn("资源不存在: {}", e.getRequestURL());
        return Result.fail(ResultCode.NOT_FOUND.getCode(), "资源不存在");
    }

    /**
     * 请求方法不支持
     *
     * @param e 方法不支持异常
     * @return 统一失败结果（400）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethod(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMethod());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求方法不支持");
    }

    /**
     * 兜底系统异常
     *
     * @param e 系统异常
     * @return 统一失败结果（500）
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.SYSTEM_ERROR.getCode(), "系统异常");
    }
}
