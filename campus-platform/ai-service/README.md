# ai-service

校园 AI 智能问答服务，基于 RAG（检索增强生成）架构，对接 DeepSeek 大模型，提供校园事务咨询能力。

**端口**: 8000 | **框架**: FastAPI + LangGraph

## 功能

- **RAG 问答** — 基于知识库的检索增强生成，同步和 SSE 流式两种模式
- **意图分类** — 自动识别闲聊、校园咨询、无关问题三类意图
- **混合检索** — 向量相似度搜索 + BM25 关键词搜索，RRF 融合排序
- **查询改写** — LLM 生成 2-3 个改写查询，提高召回率
- **重排序** — CrossEncoder 精排，低质量结果自动降级为兜底回复
- **知识库管理** — 文档上传/编辑/删除/启用/重建索引

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/chat` | 同步问答 |
| `POST` | `/chat/stream` | SSE 流式问答 |
| `POST` | `/knowledge/upload` | 上传知识文档 |
| `GET` | `/knowledge/list` | 知识文档列表 |
| `GET` | `/knowledge/{filename}/content` | 获取文档内容 |
| `PUT` | `/knowledge/{filename}` | 更新文档 |
| `POST` | `/knowledge/toggle/{filename}` | 启用/禁用文档 |
| `DELETE` | `/knowledge/{filename}` | 删除文档 |
| `POST` | `/knowledge/rebuild` | 重建知识库索引 |
| `GET` | `/knowledge/rebuild/status` | 重建进度 |
| `GET` | `/health` | 健康检查 |

## RAG 流水线

```
用户提问
  │
  ▼
classify（意图分类）
  ├── chat    → chat_reply（闲聊回复）
  ├── reject  → reject_reply（拒绝无关问题）
  └── rag     → rewrite（查询改写）
                  │
                  ▼
              retrieve（混合检索）
              ├── 向量搜索（ChromaDB + BGE Embedding）
              └── BM25 关键词搜索（jieba 分词）
                  │
                  ▼
              rerank（CrossEncoder 精排）
                  │
              ┌───┴───┐
              ▼       ▼
           generate  fallback（质量不足时兜底）
              │
              ▼
          生成回答（带来源引用）
```

## 技术栈

| 组件 | 技术 |
|---|---|
| Web 框架 | FastAPI + Uvicorn |
| LLM | DeepSeek Chat（OpenAI 兼容 API） |
| 向量模型 | BAAI/bge-small-zh-v1.5（本地 HuggingFace） |
| 重排序 | BAAI/bge-reranker-base（CrossEncoder） |
| 向量数据库 | ChromaDB（本地持久化） |
| 编排框架 | LangGraph（状态图） |
| 文档转换 | markitdown（PDF/DOCX → Markdown） |
| 中文分词 | jieba |
| 关键词搜索 | rank-bm25（BM25Okapi） |

## 知识库

- 支持格式：`.md`、`.txt`、`.pdf`、`.docx`、`.doc`（非 Markdown 自动转换）
- 分块策略：Markdown 感知分块，按标题层级切分，目标 500 字/块，50 字重叠
- 存储：`./chroma_data`（向量）、`./knowledge`（原始文档）
- 配置：`_config.json` 管理文档启用状态

## 环境变量

| 变量 | 说明 |
|---|---|
| `OPENAI_API_KEY` | DeepSeek API Key |
| `OPENAI_BASE_URL` | DeepSeek API 地址 |
| `LLM_MODEL` | 模型名称（默认 deepseek-chat） |
| `EMBEDDING_MODEL` | Embedding 模型（默认 BAAI/bge-small-zh-v1.5） |
| `CHROMA_PATH` | ChromaDB 存储路径 |

## 启动

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

## Docker

```bash
docker build -t campus-ai-service .
docker run -p 8000:8000 campus-ai-service
```
