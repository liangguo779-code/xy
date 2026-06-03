package com.campus.ai.service;

import com.campus.ai.dto.ChatRequest;
import com.campus.ai.dto.ChatResponse;

public interface AiService {

    ChatResponse chat(ChatRequest request);
}
