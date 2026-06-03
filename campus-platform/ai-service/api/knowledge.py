import os
import shutil
from pathlib import Path
from fastapi import APIRouter, UploadFile, File, HTTPException
from rag.loader import load_documents
from rag.splitter import split_documents
from rag.retriever import add_documents

router = APIRouter()

KNOWLEDGE_DIR = "./knowledge"


@router.post("/knowledge/upload")
async def upload_document(file: UploadFile = File(...)):
    """上传知识库文档并自动入库"""
    allowed_suffixes = {".txt", ".pdf", ".docx", ".doc", ".md"}
    suffix = Path(file.filename).suffix.lower()
    if suffix not in allowed_suffixes:
        raise HTTPException(status_code=400, detail=f"不支持的文件格式: {suffix}")

    # 保存文件
    os.makedirs(KNOWLEDGE_DIR, exist_ok=True)
    file_path = os.path.join(KNOWLEDGE_DIR, file.filename)
    with open(file_path, "wb") as f:
        content = await file.read()
        f.write(content)

    # 加载、切分、入库
    docs = load_documents(KNOWLEDGE_DIR)
    # 只处理新上传的文件
    new_docs = [d for d in docs if d["source"] == file.filename]
    if not new_docs:
        raise HTTPException(status_code=500, detail="文档加载失败")

    chunks = split_documents(new_docs)
    count = add_documents(chunks)

    return {
        "message": f"文档 {file.filename} 已成功入库",
        "chunks_count": count,
    }


@router.get("/knowledge/list")
async def list_documents():
    """列出知识库中的所有文档"""
    os.makedirs(KNOWLEDGE_DIR, exist_ok=True)
    files = []
    for f in Path(KNOWLEDGE_DIR).rglob("*"):
        if f.is_file():
            files.append({
                "name": f.name,
                "size": f.stat().st_size,
                "suffix": f.suffix,
            })
    return {"files": files}


@router.post("/knowledge/rebuild")
async def rebuild_knowledge():
    """重建整个知识库向量索引"""
    docs = load_documents(KNOWLEDGE_DIR)
    if not docs:
        raise HTTPException(status_code=400, detail="知识库为空")

    chunks = split_documents(docs)
    count = add_documents(chunks)

    return {
        "message": "知识库重建完成",
        "documents": len(docs),
        "chunks": count,
    }
