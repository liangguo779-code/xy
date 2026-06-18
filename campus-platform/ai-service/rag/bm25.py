import os
import logging

logger = logging.getLogger(__name__)

_bm25_index = None
_bm25_docs = []  # 与向量库一致的 chunks: [{source, content, chunk_index, section_title?, section_path?}]


def build_bm25_from_chunks(chunks: list[dict]):
    """从向量库的 chunks 构建 BM25 索引（与向量检索 chunk 对齐）"""
    global _bm25_index, _bm25_docs

    if not chunks:
        _bm25_index = None
        _bm25_docs = []
        return

    try:
        from rank_bm25 import BM25Okapi
        import jieba

        _bm25_docs = chunks
        tokenized = [list(jieba.cut(c["content"])) for c in chunks]
        _bm25_index = BM25Okapi(tokenized)
        logger.info("BM25 索引构建完成: %d 个 chunk", len(chunks))
    except Exception as e:
        logger.warning("BM25 索引构建失败: %s", e)
        _bm25_index = None
        _bm25_docs = []


def rebuild_bm25(chunks: list[dict] = None):
    """重建 BM25 索引

    如果提供 chunks 则直接使用，否则从向量库加载。
    """
    global _bm25_index
    _bm25_index = None

    if chunks is not None:
        build_bm25_from_chunks(chunks)
    else:
        # 从向量库加载所有文档重新构建
        _rebuild_from_vectordb()


def _rebuild_from_vectordb():
    """从向量库重新加载 chunks 构建 BM25 索引"""
    try:
        from rag.retriever import get_vectorstore
        vectorstore = get_vectorstore()
        # 获取所有文档
        result = vectorstore.get(include=["documents", "metadatas"])
        if not result or not result["documents"]:
            return

        chunks = []
        for i, (doc, meta) in enumerate(zip(result["documents"], result["metadatas"])):
            chunks.append({
                "content": doc,
                "source": meta.get("source", ""),
                "chunk_index": meta.get("chunk_index", i),
                "section_title": meta.get("section_title", ""),
                "section_path": meta.get("section_path", ""),
            })

        build_bm25_from_chunks(chunks)
    except Exception as e:
        logger.warning("从向量库重建 BM25 失败: %s", e)


def bm25_search(query: str, top_k: int = 5, disabled: set = None) -> list[dict]:
    """BM25 关键词检索（支持过滤禁用文件）"""
    global _bm25_index

    if _bm25_index is None:
        _rebuild_from_vectordb()

    if _bm25_index is None or not _bm25_docs:
        return []

    import jieba

    tokenized_query = list(jieba.cut(query))
    scores = _bm25_index.get_scores(tokenized_query)

    indexed_scores = list(enumerate(scores))
    indexed_scores.sort(key=lambda x: x[1], reverse=True)

    if disabled is None:
        disabled = set()

    results = []
    for idx, score in indexed_scores:
        if score <= 0:
            continue
        doc = _bm25_docs[idx]
        if doc["source"] in disabled:
            continue
        item = doc.copy()
        item["bm25_score"] = float(score)
        results.append(item)
        if len(results) >= top_k:
            break

    return results
