def split_text(text: str, chunk_size: int = 500, chunk_overlap: int = 50) -> list[str]:
    """按段落优先切分文本，段落过长时按 chunk_size 二次切分"""
    # 先按段落切分
    paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]

    chunks = []
    current_chunk = ""

    for para in paragraphs:
        if len(current_chunk) + len(para) + 2 <= chunk_size:
            current_chunk = current_chunk + "\n\n" + para if current_chunk else para
        else:
            if current_chunk:
                chunks.append(current_chunk)
            # 段落本身超过 chunk_size，需要二次切分
            if len(para) > chunk_size:
                sub_chunks = _split_by_size(para, chunk_size, chunk_overlap)
                chunks.extend(sub_chunks)
            else:
                current_chunk = para

    if current_chunk:
        chunks.append(current_chunk)

    return chunks


def _split_by_size(text: str, chunk_size: int, overlap: int) -> list[str]:
    """按固定长度切分，保留重叠"""
    chunks = []
    start = 0
    while start < len(text):
        end = start + chunk_size
        chunks.append(text[start:end])
        start = end - overlap
    return chunks


def split_documents(documents: list[dict], chunk_size: int = 500, overlap: int = 50) -> list[dict]:
    """切分文档列表，返回 [{source, content}]"""
    result = []
    for doc in documents:
        chunks = split_text(doc["content"], chunk_size, overlap)
        for i, chunk in enumerate(chunks):
            result.append({
                "source": doc["source"],
                "content": chunk,
                "chunk_index": i,
            })
    return result
