import os
import logging
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

# DeepSeek 没有 Embedding API，统一使用本地 HuggingFace 模型
# bge-small-zh-v1.5 轻量级中文向量模型，CPU 即可运行

_embedding_model = None


def get_embedding_model():
    """获取 Embedding 模型（单例，本地 HuggingFace）"""
    global _embedding_model
    if _embedding_model is None:
        model_name = os.getenv("EMBEDDING_MODEL", "BAAI/bge-base-zh-v1.5")
        logger.info("初始化 Embedding 模型: %s", model_name)
        from langchain_community.embeddings import HuggingFaceEmbeddings
        _embedding_model = HuggingFaceEmbeddings(
            model_name=model_name,
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True},
        )
    return _embedding_model
