package com.dayflow.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 对话返回视图
 *
 * @author jiaxianming
 */
@Data
@AllArgsConstructor
public class ChatVO {

    /**
     * 模型回复内容
     */
    private String reply;

    /**
     * 实际使用的 provider（deepseek / ollama）
     */
    private String provider;

    /**
     * 实际使用的模型名
     */
    private String model;
}
