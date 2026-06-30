# campus-ai

AI 智能助手模块，提供校园 AI 对话服务和知识库管理。Java 端负责会话持久化，实际 AI 推理由 Python ai-service 完成。

## 功能概述

- AI 对话（普通/SSE 流式）
- 对话会话管理（创建、列表、删除、修改标题）
- 会话消息持久化
- 管理员：知识库文档管理（上传、编辑、启停、删除、重建索引）

## 接口列表

### AiChatController `/api/ai`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/chat` | 发送 AI 对话消息（自动创建会话，持久化） |
| POST | `/api/ai/chat/stream` | SSE 流式 AI 对话（代理转发至 Python 服务） |
| GET | `/api/ai/sessions` | 获取当前用户的 AI 会话列表 |
| GET | `/api/ai/sessions/{sessionId}/messages` | 获取会话消息历史 |
| POST | `/api/ai/sessions` | 创建新会话 |
| DELETE | `/api/ai/sessions/{sessionId}` | 删除会话 |
| PUT | `/api/ai/sessions/{sessionId}/title` | 修改会话标题 |

### AdminKnowledgeController `/api/admin/knowledge`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/knowledge/list` | 知识库文档列表 |
| POST | `/api/admin/knowledge/upload` | 上传知识文档 |
| GET | `/api/admin/knowledge/{filename}/content` | 获取文档内容（用于编辑） |
| PUT | `/api/admin/knowledge/{filename}` | 更新文档内容 |
| POST | `/api/admin/knowledge/toggle/{filename}` | 启用/停用文档 |
| DELETE | `/api/admin/knowledge/{filename}` | 删除文档 |
| POST | `/api/admin/knowledge/rebuild` | 全量重建知识库索引（异步） |
| GET | `/api/admin/knowledge/rebuild/status` | 查询重建进度 |

## 依赖

- `campus-common`（公共模块）
- 外部 Python AI 服务（FastAPI + LangChain + ChromaDB，端口 8000）
