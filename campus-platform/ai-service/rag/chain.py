import os
import logging
from dotenv import load_dotenv
from rag.retriever import search

load_dotenv()

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """你是校园事务咨询助手，专门帮助学生解答关于学校规章制度、办事流程、课程安排等问题。

请根据以下参考资料回答学生的问题。
如果资料中没有相关信息，请如实说明，并建议学生联系对应的学校部门。

**重要要求：**
1. 回答要简洁明了，条理清晰
2. 在回答的关键内容后用 [来源X] 标注引用出处
3. 回答末尾用 "---" 分隔，列出所有引用的来源详情
4. 如果学生的问题涉及之前的对话内容（例如"上一句"、"刚才"、"之前"等），请结合对话历史回答，明确告知学生之前问了什么"""

CONTEXT_TEMPLATE = """参考资料:
{context}"""


def get_llm():
    """获取 LLM 客户端"""
    api_key = os.getenv("OPENAI_API_KEY")
    base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    model = os.getenv("LLM_MODEL", "gpt-4o-mini")

    if api_key and api_key != "sk-your-api-key":
        from langchain_openai import ChatOpenAI
        return ChatOpenAI(
            model=model,
            openai_api_key=api_key,
            openai_api_base=base_url,
            temperature=0.3,
        )
    else:
        return None


def rag_query(question: str, history: list = None) -> dict:
    """RAG 问答主流程

    Args:
        question: 用户问题
        history: 对话历史 [{"role": "user/assistant", "content": "..."}]
    """
    # 1. 向量检索
    search_results = search(question, top_k=5)

    if not search_results:
        return {
            "answer": "知识库中暂无相关信息，建议您联系学校教务处或学生事务中心咨询。",
            "sources": [],
        }

    # 2. 构建带编号的上下文
    context_parts = []
    source_list = []
    for i, r in enumerate(search_results, 1):
        context_parts.append(f"[来源{i}] 来自《{r['source']}》:\n{r['content']}")
        source_list.append({
            "index": i,
            "source": r["source"],
            "chunk_index": r.get("chunk_index", 0),
            "content": r["content"],
            "score": r.get("score", 0),
        })

    context = "\n\n---\n\n".join(context_parts)

    # 3. 调用 LLM
    llm = get_llm()

    if llm is None:
        # 降级: 直接返回检索结果
        answer = "根据知识库检索，以下是相关信息：\n\n"
        for s in source_list:
            answer += f"**[来源{s['index']}]** 《{s['source']}》\n{s['content'][:300]}...\n\n"
        return {"answer": answer, "sources": source_list}

    # 4. 构建消息列表（使用正确的 chat message 格式）
    from langchain_core.messages import SystemMessage, HumanMessage, AIMessage

    messages = [SystemMessage(content=SYSTEM_PROMPT)]

    # 添加对话历史作为独立消息
    if history and len(history) > 0:
        logger.info("使用对话历史: %d 条消息", len(history))
        for msg in history[-10:]:  # 最多取最近10条
            role = msg.get("role", "")
            content = msg.get("content", "")
            if role == "user":
                messages.append(HumanMessage(content=content))
            elif role == "assistant":
                messages.append(AIMessage(content=content))
    else:
        logger.info("无对话历史")

    # 添加参考资料和当前问题
    user_message = CONTEXT_TEMPLATE.format(context=context) + f"\n\n学生问题: {question}"
    messages.append(HumanMessage(content=user_message))

    # 5. LLM 生成回答
    logger.info("发送 %d 条消息给 LLM", len(messages))
    response = llm.invoke(messages)

    return {
        "answer": response.content,
        "sources": source_list,
    }
