import os
import logging

logger = logging.getLogger(__name__)

_reranker = None


def get_reranker():
    """获取 Reranker 模型（单例）"""
    global _reranker
    if _reranker is None:
        try:
            from sentence_transformers import CrossEncoder
            model_name = os.getenv("RERANKER_MODEL", "BAAI/bge-reranker-base")
            logger.info("初始化 Reranker 模型: %s", model_name)
            _reranker = CrossEncoder(model_name, max_length=512)
        except Exception as e:
            logger.warning("Reranker 初始化失败: %s", e)
            return None
    return _reranker


def rerank(query: str, results: list[dict], top_k: int = 5) -> list[dict]:
    """对检索结果重排序

    Args:
        query: 用户查询
        results: 检索结果列表 [{content, source, ...}]
        top_k: 返回前 K 个

    Returns:
        重排后的结果列表
    """
    if not results:
        return results

    reranker = get_reranker()
    if reranker is None:
        logger.warning("Reranker 不可用，跳过重排")
        return results[:top_k]

    try:
        # 构造 query-document 对
        pairs = [[query, r["content"]] for r in results]
        scores = reranker.predict(pairs)

        # 附加 rerank 分数并排序
        for i, score in enumerate(scores):
            results[i]["rerank_score"] = float(score)

        reranked = sorted(results, key=lambda x: x.get("rerank_score", 0), reverse=True)
        logger.info("Rerank 完成: %d 个结果重排，最高分=%.4f", len(reranked), reranked[0]["rerank_score"])
        return reranked[:top_k]

    except Exception as e:
        logger.warning("Rerank 失败: %s，返回原始结果", e)
        return results[:top_k]
