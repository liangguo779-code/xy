package com.campus.ai.service.impl;

import com.campus.ai.dto.ChatRequest;
import com.campus.ai.dto.ChatResponse;
import com.campus.ai.service.AiService;
import com.campus.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    public AiServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 连接超时 10秒
        factory.setReadTimeout(120000);    // 读取超时 120秒
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            int historyCount = request.getHistory() != null ? request.getHistory().size() : 0;
            log.info("发送请求到 AI 中台: question={}, historyCount={}", request.getQuestion(), historyCount);

            HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<ChatResponse> resp = restTemplate.exchange(
                    aiServiceUrl + "/chat",
                    HttpMethod.POST,
                    entity,
                    ChatResponse.class
            );

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
            throw new BusinessException("AI 服务返回异常");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 AI 中台失败", e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }
}
