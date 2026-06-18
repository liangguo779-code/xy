import os
import logging
from dotenv import load_dotenv

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

INTENT_PROMPT = """你是意图分类助手。判断用户问题属于以下哪一类，只输出分类标签，不要解释。

分类规则：
- chat: 闲聊、问候、感谢、告别（如"你好"、"谢谢"、"再见"、"你是谁"）
- rag: 与校园事务相关的问题（如规章制度、课程、考试、奖学金、休学、宿舍、校园卡等）
- reject: 与校园完全无关的问题（如推荐电影、天气、股票、游戏攻略等）

示例：
用户: 你好 → chat
用户: 谢谢你的帮助 → chat
用户: 休学怎么办理 → rag
用户: 奖学金怎么申请 → rag
用户: 推荐个电影 → reject
用户: 今天天气怎么样 → reject
用户: 你是谁 → chat
用户: 挂科了怎么重修 → rag
"""

_llm = None  # 单例


def get_llm():
    """获取 LLM 客户端（单例）"""
    global _llm
    if _llm is not None:
        return _llm

    api_key = os.getenv("OPENAI_API_KEY")
    base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    model = os.getenv("LLM_MODEL", "gpt-4o-mini")

    if api_key and api_key != "sk-your-api-key":
        from langchain_openai import ChatOpenAI
        _llm = ChatOpenAI(
            model=model,
            openai_api_key=api_key,
            openai_api_base=base_url,
            temperature=0.3,
        )
        logger.info("LLM 初始化完成: %s", model)
        return _llm
    else:
        logger.warning("未配置有效的 API Key，LLM 不可用")
        return None
