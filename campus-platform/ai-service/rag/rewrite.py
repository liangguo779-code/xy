import os
import logging
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

REWRITE_PROMPT = """你是查询重写助手。知识库中包含《学生手册》、个人简历、实习经历等文档。

你的任务：将用户问题重写为多个检索查询，提高向量检索命中率。

规则：
1. 提取问题中的关键实体（公司名、人名、岗位、制度名称等），不要丢弃
2. 生成 2-3 个不同角度的查询，用换行分隔：
   - 第1个：保留原始关键词的正式表述
   - 第2个：换成同义词或更具体的描述
   - 第3个：如果是模糊问题，补充可能的上下文
3. 如果涉及对话历史中的内容（如"那要交钱吗"），结合历史上下文重写为完整独立查询
4. 只输出查询，不要解释，不要编号，每行一个查询

示例：
用户问题: "挂科了咋整"
学生课程考核不及格的补考与重修规定
课程成绩不合格的处理办法

用户问题: "查下我在天源迪科的实习内容"
天源迪科信息技术股份有限公司实习经历
软件开发部门全栈开发岗位实习工作内容

用户问题: "奖学金怎么申请"
学生奖学金评定与申请办法
奖学金评选条件与申请流程

用户问题: "那要交钱吗"（上一轮问的是重修）
重修课程是否需要缴纳学费
重修收费标准
"""


def rewrite_query(question: str, history: list[dict] = None, llm=None) -> list[str]:
    """用 LLM 将口语化问题重写为多个检索查询

    Args:
        question: 原始问题
        history: 对话历史 [{"role": "user/assistant", "content": "..."}]
        llm: LangChain LLM 实例

    Returns:
        重写后的查询列表（至少包含原始问题）
    """
    if llm is None:
        logger.warning("LLM 不可用，跳过查询重写")
        return [question]

    try:
        from langchain_core.messages import SystemMessage, HumanMessage, AIMessage

        messages = [SystemMessage(content=REWRITE_PROMPT)]

        # 添加最近 5 条对话历史作为上下文
        if history:
            for msg in history[-5:]:
                role = msg.get("role", "")
                content = msg.get("content", "")
                if role == "user":
                    messages.append(HumanMessage(content=content))
                elif role == "assistant":
                    messages.append(AIMessage(content=content))

        messages.append(HumanMessage(content=f"用户问题: {question}"))

        response = llm.invoke(messages)
        rewritten = response.content.strip()

        # 清理引号
        if rewritten.startswith('"') and rewritten.endswith('"'):
            rewritten = rewritten[1:-1]
        if rewritten.startswith("'") and rewritten.endswith("'"):
            rewritten = rewritten[1:-1]

        # 按换行拆分为多个查询
        queries = [q.strip() for q in rewritten.split("\n") if q.strip()]

        # 去重（忽略大小写）
        seen = set()
        unique = []
        for q in queries:
            key = q.lower()
            if key not in seen:
                seen.add(key)
                unique.append(q)

        # 确保至少有一个查询
        if not unique:
            unique = [question]

        logger.info("查询重写: '%s' → %s", question[:50], unique)
        return unique

    except Exception as e:
        logger.warning("查询重写失败: %s，使用原始问题", e)
        return [question]
