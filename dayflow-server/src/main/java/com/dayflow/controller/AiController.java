package com.dayflow.controller;

import com.dayflow.common.Result;
import com.dayflow.pojo.dto.ChatRequestDTO;
import com.dayflow.pojo.vo.ChatVO;
import com.dayflow.service.AiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 对话控制器
 * <p>/api/ai/** 由全局 JwtInterceptor 拦截，需携带有效 JWT。</p>
 *
 * @author jiaxianming
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    /**
     * @param aiService AI 对话服务
     */
    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 与模型对话
     *
     * @param dto 对话请求（message 不能为空）
     * @return 对话结果
     */
    @PostMapping("/chat")
    public Result<ChatVO> chat(@Valid @RequestBody ChatRequestDTO dto) {
        return Result.success(aiService.chat(dto));
    }
}
