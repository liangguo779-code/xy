# campus-ai

AI 智能助手模块，提供校园 AI 对话服务和知识库管理。推理全链路在 Java 内完成：LangChain4j（1.0.1）内嵌调用大模型与向量化，Elasticsearch 作向量库，无需外部 Python 服务。

## 功能概述

- AI 对话（普通/SSE 流式，SSE 由 Java 内 `RagOrchestrator` 流式推送，非代理转发）
- 对话会话管理（创建、列表、删除、修改标题）
- 会话消息持久化（MySQL）与对话记忆（Redis 读透缓存 + MySQL 持久层）
- 管理员：知识库文档管理（上传、编辑、启停、删除、重建索引）

## 架构

| 组件 | 实现 |
|------|------|
| LLM | LangChain4j `ChatModel`（openai-compatible 协议，默认 deepseek-chat），`CampusRagAssistant`（AiServices）负责生成与工具调用 |
| 向量库 | Elasticsearch 索引 `campus_knowledge`（dense_vector + cosine KNN） |
| Embedding | BGE-small-zh（`Xenova/bge-small-zh`，512 维，本地 ONNX） |
| 重排序 | BGE-reranker（`Xenova/bge-reranker-base`，cross-encoder，`langchain4j-onnx-scoring`） |
| 关键词检索 | 内存 BM25 索引（jieba 分词） |
| 文档解析 | PDFBox / POI（`DocumentConverter`），Markdown 分块（`MarkdownSectionSplitter`：标题 + `**第X条**` 条款双层切分、父子分块） |
| 对话记忆 | `MemoryStore`：Redis 分布式读透缓存，MySQL 持久化 |

### RAG 流水线

`RagOrchestrator` 总调度：**意图分类 → 查询改写 → 混合检索 → 交叉编码器重排 → 生成 → 引用校验 / 置信度分级兜底**。

1. **意图分类**（`IntentClassifier`）：区分校园问答 / 闲聊 / 无关问题（无关直接拒答）。
2. **查询改写**（`QueryRewriter`）：生成多个检索关键词。
3. **混合检索**（`HybridRetriever`）：ES KNN 向量检索 + jieba BM25 关键词检索，加权 RRF 融合（BM25 提权 4 倍、第一名额外加权，k=60）；命中子分块自动展开为父分块（完整文章）。
4. **重排**：BGE cross-encoder 打分（分数越低越匹配），保留 top 片段；分数不理想时换词重试一次。
5. **生成**：`CampusRagAssistant`（LangChain4j AiServices）携带参考片段与历史记忆生成回答。
6. **反幻觉兜底**：`CitationValidator` 校验 `[来源N]` 引用、剥离伪造引用；`ConfidenceGrader` 按重排分数分为 high / medium / low，低置信度跳过 LLM 直接返回片段摘要，中置信度附加免责声明。

SSE 流式按阶段推送进度事件（classify_done / rewrite_done / retrieve_done / rerank_done / generate_done 等）与 token。

## 接口列表

### AiChatController `/api/ai`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/chat` | 发送 AI 对话消息（自动创建会话，持久化） |
| POST | `/api/ai/chat/stream` | SSE 流式 AI 对话（Java 内 RagOrchestrator 流式推送） |
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
- LangChain4j 1.0.1（core / spring-boot-starter / open-ai / embeddings-bge-small-zh / onnx-scoring / elasticsearch）
- Elasticsearch（向量存储，索引 `campus_knowledge`）
- jieba-analysis（BM25 中文分词）、Apache PDFBox / POI（文档解析）
- Redis（对话记忆缓存）、MySQL（会话与消息持久化）
- 外部 LLM 服务：任意 openai-compatible 接口（配置 `campus.ai.llm.base-url` / `api-key` / `model`）
