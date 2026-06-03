import os
from pathlib import Path


def load_documents(doc_dir: str = "./knowledge") -> list[dict]:
    """加载 knowledge 目录下的所有文档，返回 [{source, content}]"""
    docs = []
    doc_path = Path(doc_dir)

    for file_path in doc_path.rglob("*"):
        if file_path.is_file():
            content = _load_file(file_path)
            if content:
                docs.append({
                    "source": file_path.name,
                    "content": content,
                })
    return docs


def _load_file(file_path: Path) -> str | None:
    suffix = file_path.suffix.lower()

    try:
        if suffix == ".txt":
            return file_path.read_text(encoding="utf-8")
        elif suffix == ".pdf":
            return _load_pdf(file_path)
        elif suffix in (".docx", ".doc"):
            return _load_docx(file_path)
        elif suffix == ".md":
            return file_path.read_text(encoding="utf-8")
    except Exception as e:
        print(f"加载文件失败 {file_path}: {e}")
    return None


def _load_pdf(file_path: Path) -> str | None:
    try:
        from unstructured.partition.pdf import partition_pdf
        elements = partition_pdf(str(file_path))
        return "\n\n".join(str(el) for el in elements)
    except ImportError:
        # fallback: 使用 PyPDF2
        try:
            import PyPDF2
            reader = PyPDF2.PdfReader(str(file_path))
            return "\n\n".join(page.extract_text() or "" for page in reader.pages)
        except ImportError:
            print("需要安装 PyPDF2 或 unstructured 来处理 PDF")
            return None


def _load_docx(file_path: Path) -> str | None:
    try:
        from unstructured.partition.docx import partition_docx
        elements = partition_docx(str(file_path))
        return "\n\n".join(str(el) for el in elements)
    except ImportError:
        try:
            import docx
            doc = docx.Document(str(file_path))
            return "\n\n".join(p.text for p in doc.paragraphs)
        except ImportError:
            print("需要安装 python-docx 或 unstructured 来处理 DOCX")
            return None
