package com.campus.ai.service.impl;

import com.campus.ai.dto.ChatRequest;
import com.campus.ai.dto.ChatResponse;
import com.campus.ai.service.AiService;
import com.campus.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

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

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getKnowledgeList() {
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(
                    aiServiceUrl + "/knowledge/list", Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
            throw new BusinessException("AI 服务返回异常");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取知识库列表失败", e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadKnowledge(MultipartFile file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    aiServiceUrl + "/knowledge/upload",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
            throw new BusinessException("AI 服务返回异常");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传知识库文档失败", e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> rebuildKnowledge() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    aiServiceUrl + "/knowledge/rebuild",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
            throw new BusinessException("AI 服务返回异常");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("重建知识库索引失败", e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPendingList() {
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(
                    aiServiceUrl + "/knowledge/pending", Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
            throw new BusinessException("AI 服务返回异常");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取待审核列表失败", e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> confirmKnowledge(String filename) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    aiServiceUrl + "/knowledge/confirm/" + filename,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
            throw new BusinessException("AI 服务返回异常");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("确认知识库文档失败: {}", filename, e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> toggleKnowledge(String filename) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    aiServiceUrl + "/knowledge/toggle/" + filename,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
            throw new BusinessException("AI 服务返回异常");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("切换知识库文档状态失败: {}", filename, e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public void deleteKnowledge(String filename) {
        try {
            restTemplate.delete(aiServiceUrl + "/knowledge/" + filename);
        } catch (Exception e) {
            log.error("删除知识库文档失败: {}", filename, e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public void deletePending(String filename) {
        try {
            restTemplate.delete(aiServiceUrl + "/knowledge/pending/" + filename);
        } catch (Exception e) {
            log.error("删除待审核文档失败: {}", filename, e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getKnowledgeContent(String filename) {
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(
                    aiServiceUrl + "/knowledge/" + filename + "/content", Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
            throw new BusinessException("AI 服务返回异常");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取文档内容失败: {}", filename, e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateKnowledge(String filename, String content) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of("content", content);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    aiServiceUrl + "/knowledge/" + filename,
                    HttpMethod.PUT,
                    entity,
                    Map.class
            );

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
            throw new BusinessException("AI 服务返回异常");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新文档内容失败: {}", filename, e);
            throw new BusinessException("AI 服务暂时不可用，请稍后重试");
        }
    }
}
