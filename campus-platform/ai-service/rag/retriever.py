import os
import chromadb
from langchain_chroma import Chroma
from rag.embedder import get_embedding_model

CHROMA_PATH = os.getenv("CHROMA_PATH", "./chroma_data")
COLLECTION_NAME = "campus_knowledge"


def get_vectorstore() -> Chroma:
    """获取或创建向量库"""
    embedding = get_embedding_model()
    return Chroma(
        persist_directory=CHROMA_PATH,
        embedding_function=embedding,
        collection_name=COLLECTION_NAME,
    )


def add_documents(chunks: list[dict]):
    """将切分后的文档添加到向量库"""
    vectorstore = get_vectorstore()

    texts = [c["content"] for c in chunks]
    metadatas = [{"source": c["source"], "chunk_index": c.get("chunk_index", 0)} for c in chunks]
    ids = [f"{c['source']}_{c.get('chunk_index', 0)}" for c in chunks]

    vectorstore.add_texts(texts=texts, metadatas=metadatas, ids=ids)
    return len(texts)


def search(query: str, top_k: int = 5) -> list[dict]:
    """向量检索"""
    vectorstore = get_vectorstore()
    results = vectorstore.similarity_search_with_score(query, k=top_k)

    return [
        {
            "content": doc.page_content,
            "source": doc.metadata.get("source", ""),
            "chunk_index": doc.metadata.get("chunk_index", 0),
            "score": float(score),
        }
        for doc, score in results
    ]
