import os
import logging
from pathlib import Path

logger = logging.getLogger(__name__)


def convert_to_markdown(file_path: str) -> str:
    """将 PDF/DOCX/TXT 转为 Markdown

    使用 markitdown 库进行转换，失败时降级到简单文本提取。
    返回转换后的 Markdown 文本。
    """
    suffix = Path(file_path).suffix.lower()

    if suffix == ".md":
        return Path(file_path).read_text(encoding="utf-8")

    if suffix == ".txt":
        return _txt_to_markdown(file_path)

    # 尝试使用 markitdown
    try:
        from markitdown import MarkItDown
        md = MarkItDown()
        result = md.convert(file_path)
        if result and result.text_content and len(result.text_content.strip()) > 50:
            logger.info("markitdown 转换成功: %s", file_path)
            return result.text_content
    except ImportError:
        logger.warning("markitdown 未安装，尝试降级转换")
    except Exception as e:
        logger.warning("markitdown 转换失败: %s，尝试降级", e)

    # 降级处理
    if suffix == ".pdf":
        return _pdf_to_markdown_fallback(file_path)
    elif suffix in (".docx", ".doc"):
        return _docx_to_markdown_fallback(file_path)

    raise ValueError(f"不支持的文件格式: {suffix}")


def _txt_to_markdown(file_path: str) -> str:
    """纯文本转 Markdown（简单包装）"""
    return Path(file_path).read_text(encoding="utf-8")


def _pdf_to_markdown_fallback(file_path: str) -> str:
    """PDF 降级转换"""
    try:
        import PyPDF2
        reader = PyPDF2.PdfReader(file_path)
        pages = []
        for i, page in enumerate(reader.pages, 1):
            text = page.extract_text() or ""
            if text.strip():
                pages.append(f"## 第 {i} 页\n\n{text}")
        return "\n\n".join(pages)
    except ImportError:
        raise ValueError("需要安装 markitdown 或 PyPDF2 来处理 PDF 文件")


def _docx_to_markdown_fallback(file_path: str) -> str:
    """DOCX 降级转换"""
    try:
        import docx
        doc = docx.Document(file_path)
        parts = []
        for para in doc.paragraphs:
            text = para.text.strip()
            if not text:
                continue
            style = para.style.name if para.style else ""
            if "Heading 1" in style:
                parts.append(f"# {text}")
            elif "Heading 2" in style:
                parts.append(f"## {text}")
            elif "Heading 3" in style:
                parts.append(f"### {text}")
            else:
                parts.append(text)
        return "\n\n".join(parts)
    except ImportError:
        raise ValueError("需要安装 markitdown 或 python-docx 来处理 DOCX 文件")
