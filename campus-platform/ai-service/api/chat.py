import json
import logging
from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from rag.chain import rag_query, rag_query_stream

logger = logging.getLogger(__name__)

router = APIRouter()


class HistoryItem(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    question: str
    history: list[HistoryItem] | None = []

    class Config:
        extra = "allow"  # 允许 Java 端传入 sessionId 等额外字段


class SourceItem(BaseModel):
    index: int
    source: str
    chunk_index: int = 0
    content: str = ""
    score: float = 0


class ChatResponse(BaseModel):
    answer: str
    sources: list[SourceItem]


@router.post("/chat", response_model=ChatResponse)
async def chat(req: ChatRequest):
    if not req.question.strip():
        raise HTTPException(status_code=400, detail="问题不能为空")

    history = [{"role": h.role, "content": h.content} for h in req.history] if req.history else []
    logger.info("收到请求: question=%s, history_count=%d", req.question[:50], len(history))
    result = rag_query(req.question, history=history)
    return ChatResponse(**result)


@router.post("/chat/stream")
async def chat_stream(req: ChatRequest):
    """SSE 流式问答"""
    if not req.question.strip():
        raise HTTPException(status_code=400, detail="问题不能为空")

    history = [{"role": h.role, "content": h.content} for h in req.history] if req.history else []
    logger.info("收到流式请求: question=%s, history_count=%d", req.question[:50], len(history))

    def event_generator():
        for event in rag_query_stream(req.question, history=history):
            yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
