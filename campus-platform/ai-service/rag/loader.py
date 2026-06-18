import os
import logging
from pathlib import Path

logger = logging.getLogger(__name__)


def load_documents(doc_dir: str = "./knowledge") -> list[dict]:
    """加载 knowledge 目录下的所有 .md 文件，返回 [{source, content}]

    排除 _pending 目录（待审核文件）。
    """
    docs = []
    doc_path = Path(doc_dir)

    for file_path in doc_path.rglob("*.md"):
        # 跳过 _pending 目录
        if "_pending" in file_path.parts:
            continue
        if file_path.is_file():
            try:
                content = file_path.read_text(encoding="utf-8")
                if content.strip():
                    docs.append({
                        "source": file_path.name,
                        "content": content,
                    })
            except Exception as e:
                logger.warning("加载文件失败 %s: %s", file_path, e)

    logger.info("加载了 %d 个 .md 文件", len(docs))
    return docs


def load_single_document(file_path: str) -> dict | None:
    """加载单个 .md 文件，返回 {source, content} 或 None"""
    p = Path(file_path)
    if not p.exists() or not p.is_file():
        return None
    if p.suffix.lower() != ".md":
        return None
    try:
        content = p.read_text(encoding="utf-8")
        if content.strip():
            return {"source": p.name, "content": content}
    except Exception as e:
        logger.warning("加载文件失败 %s: %s", file_path, e)
    return None
