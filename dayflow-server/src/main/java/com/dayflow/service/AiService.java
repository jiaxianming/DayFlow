package com.dayflow.service;

import com.dayflow.pojo.dto.ChatRequestDTO;
import com.dayflow.pojo.vo.ChatVO;

/**
 * AI 对话服务
 *
 * @author jiaxianming
 */
public interface AiService {

    /**
     * 与模型对话
     *
     * @param dto 对话请求
     * @return 含回复与 provider/model 元信息
     */
    ChatVO chat(ChatRequestDTO dto);
}
