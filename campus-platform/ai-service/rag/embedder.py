import os
from dotenv import load_dotenv

load_dotenv()

# DeepSeek 没有 Embedding API，统一使用本地 HuggingFace 模型
# bge-small-zh-v1.5 轻量级中文向量模型，CPU 即可运行


def get_embedding_model():
    """获取 Embedding 模型（本地 HuggingFace）"""
    from langchain_community.embeddings import HuggingFaceEmbeddings
    return HuggingFaceEmbeddings(
        model_name="BAAI/bge-small-zh-v1.5",
        model_kwargs={"device": "cpu"},
        encode_kwargs={"normalize_embeddings": True},
    )
