import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时预加载模型，避免首次请求冷启动"""
    logger.info("预加载 Embedding 模型...")
    from rag.embedder import get_embedding_model
    get_embedding_model()

    logger.info("预加载 Reranker 模型...")
    from rag.reranker import get_reranker
    get_reranker()

    logger.info("预加载完成")
    yield


app = FastAPI(title="校园 AI 中台", version="2.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

from api.chat import router as chat_router
from api.knowledge import router as knowledge_router

app.include_router(chat_router, tags=["chat"])
app.include_router(knowledge_router, tags=["knowledge"])


@app.get("/health")
async def health():
    """健康检查（验证子系统可用性）"""
    checks = {}

    # Embedding 模型
    try:
        from rag.embedder import get_embedding_model
        model = get_embedding_model()
        checks["embedding"] = "ok" if model else "not_loaded"
    except Exception as e:
        checks["embedding"] = f"error: {e}"

    # ChromaDB
    try:
        from rag.retriever import get_vectorstore
        vs = get_vectorstore()
        count = vs._collection.count()
        checks["chromadb"] = f"ok ({count} vectors)"
    except Exception as e:
        checks["chromadb"] = f"error: {e}"

    # LLM
    try:
        from rag.llm import get_llm
        llm = get_llm()
        checks["llm"] = "ok" if llm else "not_configured"
    except Exception as e:
        checks["llm"] = f"error: {e}"

    all_ok = all("ok" in str(v) or "not_configured" in str(v) for v in checks.values())
    return {"status": "ok" if all_ok else "degraded", "checks": checks}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
