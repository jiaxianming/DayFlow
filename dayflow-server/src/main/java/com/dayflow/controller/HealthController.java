package com.dayflow.controller;

import com.dayflow.common.BusinessException;
import com.dayflow.common.Result;
import com.dayflow.common.ResultCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器
 *
 * @author dayflow
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 健康检查
     *
     * @return ok
     */
    @GetMapping
    public Result<String> health() {
        return Result.success("ok");
    }

    /**
     * 异常路径（仅用于联调全局异常处理）
     *
     * @return 不返回，恒抛业务异常
     */
    @GetMapping("/error")
    public Result<Void> error() {
        throw new BusinessException(ResultCode.BUSINESS_ERROR);
    }
}
