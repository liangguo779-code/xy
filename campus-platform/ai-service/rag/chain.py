import os
import logging
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

# 预编译 LangGraph（模块级单例）
_graph = None


def _get_graph():
    """获取 LangGraph 实例（单例）"""
    global _graph
    if _graph is None:
        from rag.graph import build_graph
        _graph = build_graph()
        logger.info("LangGraph 状态图初始化完成")
    return _graph


def rag_query(question: str, history: list = None) -> dict:
    """RAG 问答主流程（通过 LangGraph 执行）

    Args:
        question: 用户问题
        history: 对话历史 [{"role": "user/assistant", "content": "..."}]
    """
    from rag.llm import get_llm

    llm = get_llm()

    # 无 LLM 时降级为简单的检索 + 返回原文
    if llm is None:
        return _fallback_query(question)

    # 通过 LangGraph 执行 Agentic RAG
    graph = _get_graph()

    initial_state = {
        "question": question,
        "history": history or [],
        "rewritten_queries": [],
        "search_results": [],
        "best_score": 999.0,
        "retry_count": 0,
        "answer": "",
        "sources": [],
        "stage": "start",
    }

    try:
        result = graph.invoke(initial_state)
        logger.info("RAG 完成: stage=%s, retry=%d", result.get("stage"), result.get("retry_count", 0))
        return {
            "answer": result.get("answer", ""),
            "sources": result.get("sources", []),
        }
    except Exception as e:
        logger.error("LangGraph 执行失败: %s", e)
        return _fallback_query(question)


def _fallback_query(question: str) -> dict:
    """降级：无 LLM 时直接返回检索结果"""
    from rag.retriever import search

    search_results = search(question, top_k=5)

    if not search_results:
        return {
            "answer": "知识库中暂无相关信息，建议您联系学校教务处或学生事务中心咨询。",
            "sources": [],
        }

    source_list = []
    answer = "根据知识库检索，以下是相关信息：\n\n"
    for i, r in enumerate(search_results, 1):
        source_list.append({
            "index": i,
            "source": r["source"],
            "chunk_index": r.get("chunk_index", 0),
            "content": r["content"],
            "score": r.get("score", 0),
        })
        answer += f"**[来源{i}]** 《{r['source']}》\n{r['content'][:300]}...\n\n"

    return {"answer": answer, "sources": source_list}


def rag_query_stream(question: str, history: list = None):
    """RAG 问答流式版本（生成器，逐 token 输出）

    Yields:
        dict: {"type": "stage", "stage": "..."} 或 {"type": "token", "content": "..."} 或 {"type": "sources", "sources": [...]}
    """
    from rag.llm import get_llm, SYSTEM_PROMPT, CONTEXT_TEMPLATE

    llm = get_llm()
    if llm is None:
        result = _fallback_query(question)
        yield {"type": "sources", "sources": result["sources"]}
        yield {"type": "token", "content": result["answer"]}
        yield {"type": "done"}
        return

    graph = _get_graph()

    initial_state = {
        "question": question,
        "history": history or [],
        "rewritten_queries": [],
        "search_results": [],
        "best_score": 999.0,
        "retry_count": 0,
        "answer": "",
        "sources": [],
        "stage": "start",
    }

    try:
        result = graph.invoke(initial_state)
    except Exception as e:
        logger.error("LangGraph 执行失败: %s", e)
        result = _fallback_query(question)
        yield {"type": "sources", "sources": result["sources"]}
        yield {"type": "token", "content": result["answer"]}
        yield {"type": "done"}
        return

    stage = result.get("stage", "")
    sources = result.get("sources", [])

    yield {"type": "sources", "sources": sources}

    # 如果是闲聊/超纲/兜底，直接返回完整回答
    if stage in ("chat_reply", "reject_reply", "fallback"):
        yield {"type": "token", "content": result.get("answer", "")}
        yield {"type": "done"}
        return

    # 如果是 generate_done/generate_fallback，流式输出
    if stage in ("generate_fallback", "generate_error"):
        yield {"type": "token", "content": result.get("answer", "")}
        yield {"type": "done"}
        return

    # 正常 generate：用 LLM 流式生成
    results = result.get("search_results", [])
    context_parts = []
    for i, r in enumerate(results[:5], 1):
        context_parts.append(f"[来源{i}] 来自《{r['source']}》:\n{r['content']}")
    context = "\n\n---\n\n".join(context_parts)

    from langchain_core.messages import SystemMessage, HumanMessage, AIMessage

    messages = [SystemMessage(content=SYSTEM_PROMPT)]
    if history:
        for msg in history[-10:]:
            role = msg.get("role", "")
            content = msg.get("content", "")
            if role == "user":
                messages.append(HumanMessage(content=content))
            elif role == "assistant":
                messages.append(AIMessage(content=content))

    user_message = CONTEXT_TEMPLATE.format(context=context) + f"\n\n学生问题: {question}"
    messages.append(HumanMessage(content=user_message))

    try:
        for chunk in llm.stream(messages):
            if chunk.content:
                yield {"type": "token", "content": chunk.content}
    except Exception as e:
        logger.error("LLM 流式生成失败: %s", e)
        yield {"type": "token", "content": result.get("answer", "")}

    yield {"type": "done"}
