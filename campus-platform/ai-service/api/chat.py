import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from rag.chain import rag_query

logger = logging.getLogger(__name__)

router = APIRouter()


class HistoryItem(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    question: str
    history: list[HistoryItem] = []


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

    history = [{"role": h.role, "content": h.content} for h in req.history]
    logger.info("收到请求: question=%s, history_count=%d", req.question[:50], len(history))
    result = rag_query(req.question, history=history)
    return ChatResponse(**result)
