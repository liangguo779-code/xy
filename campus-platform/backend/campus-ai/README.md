# campus-ai

AI 智能问答微服务，对接 Python AI 后端，提供会话管理和知识库功能。

**端口**: 8084 | **服务名**: `campus-ai-consult-service`

## 功能

- **AI 对话** — 同步问答、SSE 流式响应、历史上下文
- **会话管理** — 创建/删除/重命名会话、消息历史持久化
- **知识库管理** — 文档上传/编辑/删除、启用/禁用、重建索引（管理员）

## API 概览

| 路径前缀 | 说明 | 权限 |
|---|---|---|
| `/api/ai/chat` | 同步对话 | 公开 |
| `/api/ai/chat/stream` | SSE 流式对话 | 公开 |
| `/api/ai/sessions` | 会话管理 | 公开 |
| `/api/admin/knowledge` | 知识库管理 | 管理员 |
| `/internal/ai` | Feign 内部接口 | 服务间 |

## 架构

```
前端 → campus-ai (Spring Boot) → Python AI 服务 (http://localhost:8000)
                                    ├── /chat          同步问答
                                    ├── /chat/stream   流式问答
                                    ├── /knowledge/*   知识库管理
                                    └── /rebuild       重建索引
```

## 实体

| 实体 | 说明 |
|---|---|
| `AiChatSession` | 对话会话（userId/title） |
| `AiChatMessage` | 对话消息（role/content/sources） |

## 依赖

campus-common、campus-feign、Python AI 后端服务
