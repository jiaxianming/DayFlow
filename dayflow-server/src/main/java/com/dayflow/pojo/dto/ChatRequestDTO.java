package com.dayflow.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对话请求入参
 *
 * @author jiaxianming
 */
@Data
public class ChatRequestDTO {

    /**
     * 用户消息
     */
    @NotBlank(message = "消息不能为空")
    private String message;
}
