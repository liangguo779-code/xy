import os
import json
import time
import logging
from pathlib import Path
from langchain_chroma import Chroma
from rag.embedder import get_embedding_model

logger = logging.getLogger(__name__)

CHROMA_PATH = os.getenv("CHROMA_PATH", "./chroma_data")
KNOWLEDGE_DIR = os.getenv("KNOWLEDGE_DIR", "./knowledge")
COLLECTION_NAME = "campus_knowledge"
CONFIG_FILE = "_config.json"

_vectorstore = None  # 单例

# disabled_files 内存缓存
_disabled_cache = None
_disabled_cache_time = 0
_DISABLED_CACHE_TTL = 30  # 秒


def _load_config() -> dict:
    """加载知识库配置"""
    config_path = os.path.join(KNOWLEDGE_DIR, CONFIG_FILE)
    if os.path.exists(config_path):
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {"disabled": []}
    return {"disabled": []}


def _save_config(config: dict):
    """保存知识库配置"""
    os.makedirs(KNOWLEDGE_DIR, exist_ok=True)
    config_path = os.path.join(KNOWLEDGE_DIR, CONFIG_FILE)
    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(config, f, ensure_ascii=False, indent=2)


def _invalidate_disabled_cache():
    """失效禁用文件缓存"""
    global _disabled_cache, _disabled_cache_time
    _disabled_cache = None
    _disabled_cache_time = 0


def get_disabled_files() -> set:
    """获取已禁用的文件名集合（带 30 秒内存缓存）"""
    global _disabled_cache, _disabled_cache_time
    now = time.time()
    if _disabled_cache is not None and now - _disabled_cache_time < _DISABLED_CACHE_TTL:
        return _disabled_cache
    config = _load_config()
    _disabled_cache = set(config.get("disabled", []))
    _disabled_cache_time = now
    return _disabled_cache


def toggle_file(filename: str) -> bool:
    """切换文件启用/禁用状态，返回切换后的启用状态"""
    config = _load_config()
    disabled = set(config.get("disabled", []))

    if filename in disabled:
        disabled.remove(filename)
        enabled = True
    else:
        disabled.add(filename)
        enabled = False

    config["disabled"] = list(disabled)
    _save_config(config)
    _invalidate_disabled_cache()
    logger.info("文件 %s 已%s", filename, "启用" if enabled else "禁用")
    return enabled


def get_vectorstore() -> Chroma:
    """获取向量库实例（单例）"""
    global _vectorstore
    if _vectorstore is None:
        logger.info("初始化 Chroma 向量库: %s", CHROMA_PATH)
        embedding = get_embedding_model()
        _vectorstore = Chroma(
            persist_directory=CHROMA_PATH,
            embedding_function=embedding,
            collection_name=COLLECTION_NAME,
        )
    return _vectorstore


def reset_vectorstore():
    """重置向量库单例（在 delete_collection 后调用）"""
    global _vectorstore
    _vectorstore = None


def delete_by_source(filename: str):
    """删除指定文件的所有向量"""
    vectorstore = get_vectorstore()
    try:
        vectorstore.delete(where={"source": filename})
        logger.info("已删除文件 %s 的所有向量", filename)
    except Exception as e:
        logger.warning("删除向量失败: %s", e)


def add_documents(chunks: list[dict]):
    """将切分后的文档添加到向量库

    chunks: [{source, content, chunk_index, section_title?, section_path?}]
    """
    vectorstore = get_vectorstore()

    texts = [c["content"] for c in chunks]
    metadatas = []
    for c in chunks:
        meta = {
            "source": c["source"],
            "chunk_index": c.get("chunk_index", 0),
        }
        if c.get("section_title"):
            meta["section_title"] = c["section_title"]
        if c.get("section_path"):
            meta["section_path"] = c["section_path"]
        metadatas.append(meta)

    ids = [f"{c['source']}_{c.get('chunk_index', 0)}" for c in chunks]

    vectorstore.add_texts(texts=texts, metadatas=metadatas, ids=ids)
    logger.info("向量库写入 %d 个 chunk", len(texts))

    # 同步更新 BM25 索引（保持 chunk 对齐）
    try:
        from rag.bm25 import rebuild_bm25
        rebuild_bm25()
    except Exception as e:
        logger.warning("BM25 索引同步失败: %s", e)

    return len(texts)


def search(query: str, top_k: int = 5) -> list[dict]:
    """混合检索：向量检索 + BM25 关键词检索，RRF 融合"""
    vector_results = _vector_search(query, top_k=top_k * 2)
    bm25_results = _bm25_search(query, top_k=top_k * 2)

    # RRF 融合
    fused = _rrf_fusion(vector_results, bm25_results, k=60)
    return fused[:top_k]


def _vector_search(query: str, top_k: int = 10) -> list[dict]:
    """向量检索"""
    vectorstore = get_vectorstore()
    disabled = get_disabled_files()

    extra_k = top_k + len(disabled) * 5
    results = vectorstore.similarity_search_with_score(query, k=extra_k)

    filtered = []
    for doc, score in results:
        source = doc.metadata.get("source", "")
        if source in disabled:
            continue
        filtered.append({
            "content": doc.page_content,
            "source": source,
            "chunk_index": doc.metadata.get("chunk_index", 0),
            "section_title": doc.metadata.get("section_title", ""),
            "section_path": doc.metadata.get("section_path", ""),
            "score": float(score),
        })
        if len(filtered) >= top_k:
            break

    return filtered


def _bm25_search(query: str, top_k: int = 10) -> list[dict]:
    """BM25 关键词检索"""
    try:
        from rag.bm25 import bm25_search
        disabled = get_disabled_files()
        return bm25_search(query, top_k=top_k, disabled=disabled)
    except Exception as e:
        logger.warning("BM25 检索失败: %s", e)
        return []


def _rrf_fusion(vector_results: list[dict], bm25_results: list[dict], k: int = 60) -> list[dict]:
    """RRF (Reciprocal Rank Fusion) 融合两路检索结果

    RRF score = sum(1 / (k + rank)) for each result across all lists
    """
    scores = {}  # key -> (rrf_score, result_dict)

    for rank, r in enumerate(vector_results):
        key = f"{r['source']}_{r.get('chunk_index', 0)}"
        rrf = 1.0 / (k + rank + 1)
        if key not in scores:
            scores[key] = (0, r)
        scores[key] = (scores[key][0] + rrf, scores[key][1])

    for rank, r in enumerate(bm25_results):
        key = f"{r['source']}_{r.get('chunk_index', 0)}"
        rrf = 1.0 / (k + rank + 1)
        if key not in scores:
            scores[key] = (0, r)
        scores[key] = (scores[key][0] + rrf, scores[key][1])

    # 按 RRF 分数降序排列
    sorted_items = sorted(scores.values(), key=lambda x: x[0], reverse=True)

    result = []
    for rrf_score, r in sorted_items:
        item = r.copy()
        item["score"] = r.get("score", r.get("bm25_score", 999))
        result.append(item)

    return result
