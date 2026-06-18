import re
import logging

logger = logging.getLogger(__name__)

# Markdown 标题正则
_HEADING_RE = re.compile(r"^(#{1,6})\s+(.+)$", re.MULTILINE)


def split_markdown(text: str, source: str, chunk_size: int = 500) -> list[dict]:
    """按 Markdown 标题结构切分，保留章节层级元数据

    策略：
    1. 找到所有标题行，按标题层级构建章节树
    2. 每个章节作为一个候选 chunk
    3. 如果章节超过 chunk_size，按子标题或段落二次切分
    4. 每个 chunk 附带 section_title 和 section_path 元数据
    """
    lines = text.split("\n")
    sections = _parse_sections(lines)

    chunks = []
    for section in sections:
        section_chunks = _split_section(section, chunk_size)
        chunks.extend(section_chunks)

    # 给每个 chunk 添加 source 和 chunk_index
    for i, chunk in enumerate(chunks):
        chunk["source"] = source
        chunk["chunk_index"] = i

    logger.info("文档 %s 切分为 %d 个 chunk", source, len(chunks))
    return chunks


def _parse_sections(lines: list[str]) -> list[dict]:
    """解析 Markdown 行，提取标题结构，返回章节列表"""
    sections = []
    current_heading = None
    current_level = 0
    heading_stack = []  # [(level, title), ...]
    content_lines = []

    for line in lines:
        match = _HEADING_RE.match(line.strip())
        if match:
            # 遇到新标题，保存之前的章节
            if current_heading is not None or content_lines:
                content = "\n".join(content_lines).strip()
                if content:
                    sections.append({
                        "title": current_heading or "（无标题）",
                        "level": current_level,
                        "path": _build_path(heading_stack),
                        "content": content,
                    })

            # 更新标题栈
            level = len(match.group(1))
            title = match.group(2).strip()

            # 弹出同级或更低级的标题
            while heading_stack and heading_stack[-1][0] >= level:
                heading_stack.pop()

            heading_stack.append((level, title))
            current_heading = title
            current_level = level
            content_lines = [line]
        else:
            content_lines.append(line)

    # 保存最后一个章节
    if content_lines:
        content = "\n".join(content_lines).strip()
        if content:
            sections.append({
                "title": current_heading or "（无标题）",
                "level": current_level,
                "path": _build_path(heading_stack),
                "content": content,
            })

    # 如果没有任何标题，整个文档作为一个章节
    if not sections and lines:
        full_content = "\n".join(lines).strip()
        if full_content:
            sections.append({
                "title": "（无标题）",
                "level": 0,
                "path": "文档",
                "content": full_content,
            })

    return sections


def _build_path(heading_stack: list[tuple]) -> str:
    """构建标题路径，如 '第三章 > 第十二条 > 第2款'"""
    return " > ".join(title for _, title in heading_stack)


def _prepend_section_path(content: str, section_path: str) -> str:
    """给 chunk 内容前置章节路径，提升 embedding 和 reranker 的上下文理解"""
    if section_path and section_path != "文档":
        return f"[{section_path}]\n{content}"
    return content


def _split_section(section: dict, chunk_size: int) -> list[dict]:
    """切分单个章节，如果超过 chunk_size 则二次切分"""
    content = section["content"]

    if len(content) <= chunk_size:
        return [{
            "section_title": section["title"],
            "section_path": section["path"],
            "content": _prepend_section_path(content, section["path"]),
        }]

    # 按子标题二次切分
    sub_sections = _split_by_subheadings(content, section["path"], chunk_size)
    if len(sub_sections) > 1:
        return sub_sections

    # 没有子标题或子标题切分不够细，按段落切分
    return _split_by_paragraphs(content, section["title"], section["path"], chunk_size)


def _split_by_subheadings(content: str, parent_path: str, chunk_size: int) -> list[dict]:
    """尝试按子标题切分"""
    lines = content.split("\n")
    sub_sections = []
    current_title = ""
    current_lines = []
    current_path = parent_path

    for line in lines:
        match = _HEADING_RE.match(line.strip())
        if match and len(match.group(1)) > 1:  # 至少是 ##
            # 保存之前的子章节
            if current_lines:
                sub_content = "\n".join(current_lines).strip()
                if sub_content:
                    sub_sections.append({
                        "section_title": current_title or "（子章节）",
                        "section_path": current_path,
                        "content": _prepend_section_path(sub_content, current_path),
                    })

            current_title = match.group(2).strip()
            current_path = f"{parent_path} > {current_title}" if parent_path else current_title
            current_lines = [line]
        else:
            current_lines.append(line)

    # 保存最后一个子章节
    if current_lines:
        sub_content = "\n".join(current_lines).strip()
        if sub_content:
            sub_sections.append({
                "section_title": current_title or "（子章节）",
                "section_path": current_path,
                "content": _prepend_section_path(sub_content, current_path),
            })

    # 如果切分后每个 chunk 都在 chunk_size 内，直接返回
    if all(len(s["content"]) <= chunk_size for s in sub_sections):
        return sub_sections

    # 否则对超大子章节递归切分
    result = []
    for s in sub_sections:
        if len(s["content"]) <= chunk_size:
            result.append(s)
        else:
            result.extend(_split_by_paragraphs(s["content"], s["section_title"], s["section_path"], chunk_size))
    return result


def _split_by_paragraphs(content: str, title: str, path: str, chunk_size: int) -> list[dict]:
    """按段落切分，合并小段落直到接近 chunk_size"""
    paragraphs = [p.strip() for p in content.split("\n\n") if p.strip()]
    chunks = []
    current_chunk = ""

    for para in paragraphs:
        if len(current_chunk) + len(para) + 2 <= chunk_size:
            current_chunk = current_chunk + "\n\n" + para if current_chunk else para
        else:
            if current_chunk:
                chunks.append({
                    "section_title": title,
                    "section_path": path,
                    "content": _prepend_section_path(current_chunk, path),
                })
            # 段落本身超过 chunk_size，按固定长度切分
            if len(para) > chunk_size:
                sub_chunks = _split_by_size(para, chunk_size, overlap=50)
                for sc in sub_chunks:
                    chunks.append({
                        "section_title": title,
                        "section_path": path,
                        "content": _prepend_section_path(sc, path),
                    })
            else:
                current_chunk = para

    if current_chunk:
        chunks.append({
            "section_title": title,
            "section_path": path,
            "content": _prepend_section_path(current_chunk, path),
        })

    return chunks


def _split_by_size(text: str, chunk_size: int, overlap: int = 50) -> list[str]:
    """按固定长度切分，保留重叠"""
    if overlap >= chunk_size:
        overlap = chunk_size // 4  # 防止无限循环
    chunks = []
    start = 0
    while start < len(text):
        end = start + chunk_size
        chunks.append(text[start:end])
        start = end - overlap
    return chunks


def split_documents(documents: list[dict], chunk_size: int = 500) -> list[dict]:
    """切分文档列表，返回 [{source, content, chunk_index, section_title, section_path}]"""
    result = []
    for doc in documents:
        chunks = split_markdown(doc["content"], doc["source"], chunk_size)
        result.extend(chunks)
    return result
