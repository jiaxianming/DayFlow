package com.dayflow.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 学习笔记创建入参（M1 只存原文，不做切块 embedding）
 *
 * @author jiaxianming
 */
@Data
public class NoteCreateDTO {

    /**
     * 笔记标题
     */
    @NotBlank(message = "标题不能为空")
    private String title;

    /**
     * 笔记正文
     */
    @NotBlank(message = "正文不能为空")
    private String content;

    /**
     * 标签（逗号分隔，可空）
     */
    private String tags;
}
