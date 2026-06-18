import os
import logging
from typing import TypedDict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

SCORE_THRESHOLD = float(os.getenv("SCORE_THRESHOLD", "0.8"))
MAX_RETRIES = 1


class RAGState(TypedDict):
    question: str
    history: list[dict]
    rewritten_queries: list[str]
    search_results: list[dict]
    best_score: float
    retry_count: int
    answer: str
    sources: list[dict]
    stage: str


def build_graph():
    """构建 LangGraph 状态图"""
    from langgraph.graph import StateGraph, END

    graph = StateGraph(RAGState)

    graph.add_node("classify", node_classify)
    graph.add_node("rewrite", node_rewrite)
    graph.add_node("retrieve", node_retrieve)
    graph.add_node("rerank", node_rerank)
    graph.add_node("retry", node_retry)
    graph.add_node("generate", node_generate)
    graph.add_node("fallback", node_fallback)
    graph.add_node("chat_reply", node_chat_reply)
    graph.add_node("reject_reply", node_reject_reply)

    graph.set_entry_point("classify")

    graph.add_conditional_edges(
        "classify",
        route_after_classify,
        {
            "rag": "rewrite",
            "chat": "chat_reply",
            "reject": "reject_reply",
        },
    )
    graph.add_edge("chat_reply", END)
    graph.add_edge("reject_reply", END)

    graph.add_edge("rewrite", "retrieve")
    graph.add_edge("retrieve", "rerank")
    graph.add_conditional_edges(
        "rerank",
        route_after_rerank,
        {
            "generate": "generate",
            "retry": "retry",
            "fallback": "fallback",
        },
    )
    graph.add_conditional_edges(
        "retry",
        route_after_retry,
        {
            "generate": "generate",
            "fallback": "fallback",
        },
    )
    graph.add_edge("generate", END)
    graph.add_edge("fallback", END)

    return graph.compile()


def node_classify(state: RAGState) -> dict:
    """节点0：意图分类"""
    from rag.llm import get_llm, INTENT_PROMPT

    llm = get_llm()
    if llm is None:
        return {"intent": "rag", "stage": "classify_skipped"}

    try:
        from langchain_core.messages import SystemMessage, HumanMessage
        messages = [
            SystemMessage(content=INTENT_PROMPT),
            HumanMessage(content=f"用户: {state['question']}"),
        ]
        response = llm.invoke(messages)
        intent = response.content.strip().lower()

        if intent not in ("chat", "rag", "reject"):
            intent = "rag"

        logger.info("意图分类: '%s' → %s", state["question"][:30], intent)
        return {"intent": intent, "stage": "classify_done"}
    except Exception as e:
        logger.warning("意图分类失败: %s，默认走 RAG", e)
        return {"intent": "rag", "stage": "classify_error"}


def node_chat_reply(state: RAGState) -> dict:
    """闲聊回复"""
    from rag.llm import get_llm
    llm = get_llm()

    if llm is None:
        return {"answer": "你好！我是校园事务咨询助手，有什么可以帮你的吗？", "sources": [], "stage": "chat_reply"}

    try:
        from langchain_core.messages import SystemMessage, HumanMessage
        messages = [
            SystemMessage(content="你是校园事务咨询助手。用户在和你闲聊，请简短友好地回复，然后引导用户提出校园相关的问题。回复不超过两句话。"),
            HumanMessage(content=state["question"]),
        ]
        response = llm.invoke(messages)
        return {"answer": response.content, "sources": [], "stage": "chat_reply"}
    except Exception:
        return {"answer": "你好！我是校园事务咨询助手，有什么可以帮你的吗？", "sources": [], "stage": "chat_reply"}


def node_reject_reply(state: RAGState) -> dict:
    """超纲拒绝"""
    return {
        "answer": "抱歉，我只能回答校园事务相关的问题（如规章制度、课程考试、奖学金、休学等）。如果你有校园相关的问题，随时可以问我！",
        "sources": [],
        "stage": "reject_reply",
    }


def route_after_classify(state: RAGState) -> str:
    """路由：根据意图分类结果分发"""
    return state.get("intent", "rag")


def node_rewrite(state: RAGState) -> dict:
    """节点1：意图揣测 + Query 重写（多查询）"""
    from rag.llm import get_llm
    from rag.rewrite import rewrite_query

    llm = get_llm()
    if llm is None:
        return {"rewritten_queries": [state["question"]], "stage": "rewrite_skipped"}

    queries = rewrite_query(state["question"], state.get("history"), llm)
    return {"rewritten_queries": queries, "stage": "rewrite_done"}


def _search_single(query: str) -> list[dict]:
    """单个查询的检索（用于并发）"""
    from rag.retriever import search
    return search(query, top_k=5)


def node_retrieve(state: RAGState) -> dict:
    """节点2：多查询并发向量检索 + 合并去重"""
    queries = state.get("rewritten_queries") or [state["question"]]

    # 并发执行多个查询
    all_results = []
    with ThreadPoolExecutor(max_workers=len(queries)) as executor:
        futures = {executor.submit(_search_single, q): q for q in queries}
        for future in as_completed(futures):
            try:
                results = future.result()
                all_results.extend(results)
            except Exception as e:
                logger.warning("查询检索失败: %s", e)

    merged = _merge_results([], all_results)
    best_score = min(r.get("score", 999) for r in merged) if merged else 999.0

    logger.info("并发检索完成: %d 个查询, %d 个结果, 最佳分数=%.4f",
                len(queries), len(merged), best_score)
    return {
        "search_results": merged,
        "best_score": best_score,
        "stage": "retrieve_done",
    }


def node_rerank(state: RAGState) -> dict:
    """节点3：Reranker 重排序"""
    from rag.reranker import rerank

    question = state["question"]
    results = state.get("search_results", [])

    if not results:
        return {"stage": "rerank_done"}

    reranked = rerank(question, results, top_k=5)
    # CrossEncoder 分数是 logits（越高越相关），需要转换为 L2 距离语义（越低越相关）
    # 转换公式：score = -rerank_score，这样高相关 → 负数（低值）→ 通过阈值
    for r in reranked:
        rerank_score = r.get("rerank_score", 0)
        r["score"] = -rerank_score  # 取负，让路由逻辑 best_score <= threshold 正确工作
    best_score = reranked[0].get("score", 999) if reranked else 999.0

    logger.info("Rerank 完成: %d 个结果, 最佳分数=%.4f", len(reranked), best_score)
    return {
        "search_results": reranked,
        "best_score": best_score,
        "stage": "rerank_done",
    }


def node_retry(state: RAGState) -> dict:
    """节点4：变换关键词扩检"""
    from rag.llm import get_llm
    from rag.retriever import search

    retry_count = state.get("retry_count", 0) + 1
    last_queries = state.get("rewritten_queries") or [state["question"]]
    last_query = last_queries[0]
    best_score = state.get("best_score", 999)

    llm = get_llm()
    new_query = last_query

    if llm is not None:
        try:
            from langchain_core.messages import SystemMessage, HumanMessage

            retry_prompt = f"""你第一次用 '{last_query}' 检索，最高匹配度只有 {best_score:.2f}（L2距离，越小越相似）。
请变换关键词或换一种表述方式，生成一个新的检索词。
只输出新的检索词，不要解释。"""

            messages = [
                SystemMessage(content="你是检索关键词优化助手。"),
                HumanMessage(content=retry_prompt),
            ]
            response = llm.invoke(messages)
            new_query = response.content.strip().strip('"').strip("'")
            logger.info("重试检索: '%s' → '%s'", last_query[:30], new_query[:30])
        except Exception as e:
            logger.warning("重试查询生成失败: %s", e)

    results = search(new_query, top_k=5)
    new_best_score = min(r.get("score", 999) for r in results) if results else 999.0
    merged = _merge_results(state.get("search_results", []), results)

    return {
        "search_results": merged,
        "best_score": new_best_score,
        "retry_count": retry_count,
        "stage": "retry_done",
    }


def node_generate(state: RAGState) -> dict:
    """节点5：LLM 生成回答"""
    from rag.llm import get_llm, SYSTEM_PROMPT, CONTEXT_TEMPLATE

    llm = get_llm()
    results = state.get("search_results", [])

    context_parts = []
    source_list = []
    for i, r in enumerate(results[:5], 1):
        context_parts.append(f"[来源{i}] 来自《{r['source']}》:\n{r['content']}")
        source_list.append({
            "index": i,
            "source": r["source"],
            "chunk_index": r.get("chunk_index", 0),
            "content": r["content"],
            "score": r.get("score", 0),
        })

    context = "\n\n---\n\n".join(context_parts)

    if llm is None:
        answer = _format_fallback_answer(source_list)
        return {"answer": answer, "sources": source_list, "stage": "generate_fallback"}

    try:
        from langchain_core.messages import SystemMessage, HumanMessage, AIMessage

        messages = [SystemMessage(content=SYSTEM_PROMPT)]

        history = state.get("history", [])
        if history:
            for msg in history[-10:]:
                role = msg.get("role", "")
                content = msg.get("content", "")
                if role == "user":
                    messages.append(HumanMessage(content=content))
                elif role == "assistant":
                    messages.append(AIMessage(content=content))

        question = state["question"]
        user_message = CONTEXT_TEMPLATE.format(context=context) + f"\n\n学生问题: {question}"
        messages.append(HumanMessage(content=user_message))

        logger.info("发送 %d 条消息给 LLM", len(messages))
        response = llm.invoke(messages)

        return {
            "answer": response.content,
            "sources": source_list,
            "stage": "generate_done",
        }

    except Exception as e:
        logger.error("LLM 生成失败: %s", e)
        answer = _format_fallback_answer(source_list)
        return {"answer": answer, "sources": source_list, "stage": "generate_error"}


def node_fallback(state: RAGState) -> dict:
    """节点6：红线拦截兜底"""
    logger.info("触发兜底: 原始问题='%s'", state["question"][:50])
    return {
        "answer": "同学你好，在《学生手册》中未查询到相关规定。该问题可能涉及教务处具体业务，建议您联系教务处或学生事务中心咨询。",
        "sources": [],
        "stage": "fallback",
    }


def route_after_rerank(state: RAGState) -> str:
    """路由：Rerank 后决定走 generate / retry / fallback"""
    best_score = state.get("best_score", 999)
    search_results = state.get("search_results", [])

    if not search_results:
        return "fallback"

    if best_score <= SCORE_THRESHOLD:
        return "generate"
    else:
        return "retry"


def route_after_retry(state: RAGState) -> str:
    """路由：重试后决定走 generate / fallback"""
    best_score = state.get("best_score", 999)

    if best_score <= SCORE_THRESHOLD:
        return "generate"
    else:
        return "fallback"


def _merge_results(original: list[dict], new: list[dict]) -> list[dict]:
    """合并检索结果，去重并取分数最好的"""
    seen = {}
    for r in original + new:
        key = f"{r['source']}_{r.get('chunk_index', 0)}"
        if key not in seen or r.get("score", 999) < seen[key].get("score", 999):
            seen[key] = r

    merged = sorted(seen.values(), key=lambda x: x.get("score", 999))
    return merged[:10]


def _format_fallback_answer(source_list: list[dict]) -> str:
    """格式化降级回答（无 LLM 时）"""
    answer = "根据知识库检索，以下是相关信息：\n\n"
    for s in source_list:
        answer += f"**[来源{s['index']}]** 《{s['source']}》\n{s['content'][:300]}...\n\n"
    return answer
