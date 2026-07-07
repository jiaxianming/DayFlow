package com.dayflow.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一返回测试
 */
class ResultTest {

    /**
     * success() 无数据
     */
    @Test
    void successWithoutData() {
        Result<Void> result = Result.success();
        assertEquals(200, result.getCode());
        assertEquals("成功", result.getMessage());
        assertNull(result.getData());
    }

    /**
     * success(data) 带数据
     */
    @Test
    void successWithData() {
        Result<String> result = Result.success("ok");
        assertEquals(200, result.getCode());
        assertEquals("ok", result.getData());
    }

    /**
     * fail(ResultCode)
     */
    @Test
    void failWithResultCode() {
        Result<Void> result = Result.fail(ResultCode.BUSINESS_ERROR);
        assertEquals(500, result.getCode());
        assertEquals("业务异常", result.getMessage());
    }

    /**
     * fail(code, message)
     */
    @Test
    void failWithCodeAndMessage() {
        Result<Void> result = Result.fail(400, "参数错误");
        assertEquals(400, result.getCode());
        assertEquals("参数错误", result.getMessage());
    }
}
