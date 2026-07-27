# `com.campus.ai` — 包结构说明

`campus-ai` 模块把原来 `campus-platform/ai-service`（Python FastAPI + LangChain + LangGraph）整套
搬到了 Java 17 / Spring Boot 3.2.5 上，用 **LangChain4j** 1.0.1 + jieba + BGE reranker 实现。
外部协议不变：MySQL 库表（`ai_chat_session`、`ai_chat_message`）不变、`POST /api/ai/chat` 不变、
SSE 事件顺序 `session → stage → sources → token → done` 不变、前端无需修改。

## 推荐阅读路径

打开这个模块时按下面的顺序读，10–15 分钟就能把流水线理清：

```
1. README.md (本文件)
2. rag/RagOrchestrator.java        ← 流水线入口，节点按 LangGraph 的 while 串起来
3. rag/IntentClassifier.java       ← 意图分类（chat/rag/reject）
4. rag/QueryRewriter.java          ← 多查询改写
5. rag/retrieval/HybridRetriever.java + VectorStoreFacade.java + Bm25Index.java
                                    ← 向量 + BM25 + RRF 混合检索
6. rag/scoring/BgeRerankerScoringModel.java
                                    ← BGE cross-encoder reranker
7. knowledge/KnowledgeService.java  ← 知识库管理（list/upload/update/delete/rebuild）
8. knowledge/pipeline/MarkdownSectionSplitter.java + DocumentConverter.java
                                    ← 文档 → 段落 → chunk
9. chat/AiChatController.java       ← 对外 HTTP 入口（同步 + SSE）
10. config/LangChain4jConfig.java   ← Spring 装配：哪些 bean，怎么配
```

## 目录结构

```
com.campus.ai
├── README.md                              ← 本文件
├── config/                                ← Spring 装配层（先读 10）
│   ├── AiProperties.java                  ←  @ConfigurationProperties("campus.ai")
│   ├── LangChain4jConfig.java             ←  ChatModel / EmbeddingModel / ScoringModel / ES EmbeddingStore
│   ├── HuggingFaceModelLoader.java        ←  首次启动下载 BGE reranker 模型文件
│   ├── RebuildStatusRegistry.java         ←  /api/admin/knowledge/rebuild/status 的内存状态机
│   └── RebuildStatusRegistryConfig.java
│
├── rag/                                   ← ❶ RAG 流水线（核心，从 RagOrchestrator 入手）
│   ├── RagOrchestrator.java               ←  intent → rewrite → retrieve → rerank → retry → generate / fallback
│   ├── IntentClassifier.java              ←  LLM 分类 chat / rag / reject
│   ├── QueryRewriter.java                 ←  LLM 多查询改写
│   ├── prompt/                            ←  (预留) 中文提示词集中管理
│   ├── retrieval/                         ←  混合检索
│   │   ├── HybridRetriever.java           ←  RRF 融合 (k=60)
│   │   ├── VectorStoreFacade.java         ←  ES dense-vector 包装
│   │   └── Bm25Index.java                 ←  BM25Okapi + jieba，JSON 快照持久化
│   └── scoring/
│       └── BgeRerankerScoringModel.java   ←  BGE cross-encoder (Xenova/bge-reranker-base)
│
├── knowledge/                            ← ❷ 知识库管理
│   ├── KnowledgeService.java              ←  list / upload / update / delete / toggle / rebuild
│   ├── controller/
│   │   └── AdminKnowledgeController.java
│   ├── pipeline/                          ←  文档 → chunk
│   │   ├── DocumentConverter.java         ←  .md/.txt 直读、.pdf PDFBox、.docx POI
│   │   ├── MarkdownSectionSplitter.java   ←  按 #/##/### 分段，chunkSize=500 overlap=50
│   │   └── IngestionQueue.java            ←  后台线程池（替换 FastAPI BackgroundTasks）
│   ├── store/
│   │   └── DisabledFilesCache.java        ←  _config.json + 30s TTL
│   └── model/                              ←  DTO
│       ├── ChunkDto.java                  ←  一个 chunk 的 source/chunk_index/section_title/path/content
│       └── SourceItem.java                ←  引用列表项（与 ChatResponse.SourceItem 兼容）
│
└── chat/                                 ← ❸ 聊天会话（保留 MySQL 库表）
    ├── AiChatController.java              ←  /api/ai/chat + /api/ai/chat/stream (SSE)
    ├── AiHealthController.java            ←  /api/ai/health
    ├── AiService.java + impl/AiServiceImpl.java
                                            ←  面向 controller 的服务接口
    ├── AiChatHistoryService.java + impl/AiChatHistoryServiceImpl.java
                                            ←  会话/消息持久化（MyBatis-Plus）
    ├── dto/                                ←  对外 DTO（前端契约）
    │   ├── ChatRequest.java
    │   ├── ChatResponse.java
    │   ├── AiChatSessionVO.java
    │   └── AiChatMessageVO.java
    ├── entity/                             ←  MyBatis-Plus 实体
    │   ├── AiChatSession.java
    │   └── AiChatMessage.java
    └── mapper/                             ←  MyBatis-Plus BaseMapper
        ├── AiChatSessionMapper.java
        └── AiChatMessageMapper.java
```

## 关键配置

| 项 | 默认 | 来源 |
| --- | --- | --- |
| LLM baseUrl | `https://api.deepseek.com` | `campus.ai.llm.base-url`（被 `.env` 里的 `OPENAI_BASE_URL` 覆盖） |
| LLM apiKey | 必填 | `campus.ai.llm.api-key`（被 `.env` 里的 `OPENAI_API_KEY` 覆盖） |
| LLM model | `deepseek-chat` | `campus.ai.llm.model` |
| Embedding 维度 | 512 | `campus.ai.vector.embedding-dim` |
| 向量库索引 | `campus_knowledge`（ES dense_vector） | `campus.ai.vector.index-name` |
| BGE reranker 路径 | `${campus.ai.home}/models/bge-reranker-base/` | 首次启动自动下载 |
| 知识库目录 | `${campus.ai.home}/knowledge/` | 首次启动从 `classpath:knowledge/` 拷贝种子 |
| Rerank 阈值 | 0.8 | `campus.ai.knowledge.score-threshold` |

`.env` 文件由 `me.paulschwarz:spring-dotenv` 在启动时加载，仓库根 `.gitignore` 已排除。

## 上下游

- 上游：浏览器、Vue 前端（`frontend/src/api/ai.js`、`views/ai/AiChat.vue`、`views/admin/KnowledgeManage.vue`）
- 下游：MySQL（chat history）、Elasticsearch（向量库 + 业务搜索）、DeepSeek（LLM）、HuggingFace Hub（首次启动下载 BGE 模型）
- 启动后端：根目录 `bash start-dev.sh`
- 查看 LLM/向量/BM25 状态：`curl http://localhost:9000/api/ai/health`
