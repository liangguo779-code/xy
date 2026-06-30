import os
import time
import logging
from pathlib import Path
from fastapi import APIRouter, UploadFile, File, HTTPException, BackgroundTasks
from pydantic import BaseModel
from rag.loader import load_documents, load_single_document
from rag.splitter import split_documents
from rag.retriever import add_documents, get_vectorstore, reset_vectorstore, get_disabled_files, toggle_file, delete_by_source
from rag.converter import convert_to_markdown
from rag.bm25 import rebuild_bm25

logger = logging.getLogger(__name__)

router = APIRouter()

# 重建状态跟踪
_rebuild_status = {
    "running": False,
    "progress": "",
    "completed": False,
    "result": None,
    "error": None,
}

KNOWLEDGE_DIR = os.getenv("KNOWLEDGE_DIR", "./knowledge")
MD_SUFFIXES = {".md"}
CONVERTIBLE_SUFFIXES = {".txt", ".pdf", ".docx", ".doc"}
MAX_FILE_SIZE = 20 * 1024 * 1024  # 20MB


def _safe_filename(filename: str) -> str:
    return Path(filename).name


def _convert_index_and_save(temp_path: str, safe_name: str):
    """后台任务：转换非 MD 文件 → 保存 .md → 入库"""
    try:
        md_content = convert_to_markdown(temp_path)
        md_filename = Path(safe_name).stem + ".md"
        md_path = os.path.join(KNOWLEDGE_DIR, md_filename)
        with open(md_path, "w", encoding="utf-8") as f:
            f.write(md_content)

        doc = load_single_document(md_path)
        if doc:
            chunks = split_documents([doc])
            count = add_documents(chunks)
            rebuild_bm25()
            logger.info("文档 %s → %s 已转换并入库，%d 个 chunk", safe_name, md_filename, count)
    except Exception as e:
        logger.error("文档 %s 转换入库失败: %s", safe_name, e)
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)


def _index_md_file(safe_name: str):
    """后台任务：将 MD 文件切分并写入向量库"""
    try:
        file_path = os.path.join(KNOWLEDGE_DIR, safe_name)
        doc = load_single_document(file_path)
        if doc:
            chunks = split_documents([doc])
            count = add_documents(chunks)
            rebuild_bm25()
            logger.info("文档 %s 已入库，%d 个 chunk", safe_name, count)
    except Exception as e:
        logger.error("文档 %s 入库失败: %s", safe_name, e)


def _rebuild_all():
    """后台任务：重建整个索引"""
    global _rebuild_status
    _rebuild_status.update(running=True, completed=False, progress="正在加载文档...", error=None)
    try:
        docs = load_documents(KNOWLEDGE_DIR)
        if not docs:
            _rebuild_status.update(running=False, completed=True, progress="知识库为空，跳过重建", result={"docs": 0, "chunks": 0})
            logger.warning("知识库为空，跳过重建")
            return

        _rebuild_status["progress"] = "正在清空旧索引..."
        try:
            import chromadb
            from rag.retriever import CHROMA_PATH, COLLECTION_NAME
            client = chromadb.PersistentClient(path=CHROMA_PATH)
            client.delete_collection(COLLECTION_NAME)
            logger.info("已删除旧集合: %s", COLLECTION_NAME)
        except Exception as e:
            logger.warning("删除集合失败（可能不存在）: %s", e)
        reset_vectorstore()

        _rebuild_status["progress"] = f"正在切分文档 ({len(docs)} 个)..."
        chunks = split_documents(docs)

        _rebuild_status["progress"] = f"正在写入向量库 ({len(chunks)} 个 chunk)..."
        count = add_documents(chunks)

        _rebuild_status.update(running=False, completed=True, progress="重建完成", result={"docs": len(docs), "chunks": count})
        logger.info("知识库重建完成: %d 个文档, %d 个 chunk", len(docs), count)
    except Exception as e:
        _rebuild_status.update(running=False, completed=True, progress="重建失败", error=str(e))
        logger.error("知识库重建失败: %s", e)


@router.post("/knowledge/upload")
async def upload_document(file: UploadFile = File(...), background_tasks: BackgroundTasks = None):
    """上传知识库文档（全部自动入库）"""
    suffix = Path(file.filename).suffix.lower()

    if suffix not in MD_SUFFIXES and suffix not in CONVERTIBLE_SUFFIXES:
        raise HTTPException(status_code=400, detail=f"不支持的文件格式: {suffix}")

    content = await file.read()
    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(status_code=400, detail=f"文件大小超过限制（最大 {MAX_FILE_SIZE // 1024 // 1024}MB）")

    os.makedirs(KNOWLEDGE_DIR, exist_ok=True)
    safe_name = _safe_filename(file.filename)

    if suffix in MD_SUFFIXES:
        target_path = os.path.join(KNOWLEDGE_DIR, safe_name)
        with open(target_path, "wb") as f:
            f.write(content)
        background_tasks.add_task(_index_md_file, safe_name)
        return {"message": f"文档 {safe_name} 已上传，正在后台入库...", "status": "processing"}
    else:
        temp_path = os.path.join(KNOWLEDGE_DIR, f"_temp_{safe_name}")
        with open(temp_path, "wb") as f:
            f.write(content)
        background_tasks.add_task(_convert_index_and_save, temp_path, safe_name)
        return {"message": f"文档 {safe_name} 已上传，正在后台转换并入库...", "status": "processing"}


@router.get("/knowledge/list")
async def list_documents():
    """列出已入库的知识库文档（含启用/禁用状态）"""
    os.makedirs(KNOWLEDGE_DIR, exist_ok=True)
    disabled = get_disabled_files()
    files = []
    for f in Path(KNOWLEDGE_DIR).rglob("*.md"):
        if "_pending" in f.parts or f.name.startswith("_temp_"):
            continue
        if f.is_file():
            files.append({
                "name": f.name,
                "size": f.stat().st_size,
                "suffix": f.suffix,
                "enabled": f.name not in disabled,
            })
    return {"files": files}


@router.get("/knowledge/{filename}/content")
async def get_document_content(filename: str):
    """获取文档内容（用于编辑）"""
    safe_name = _safe_filename(filename)
    file_path = os.path.join(KNOWLEDGE_DIR, safe_name)
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail=f"文件不存在: {safe_name}")

    content = Path(file_path).read_text(encoding="utf-8")
    return {"filename": safe_name, "content": content}


class UpdateContentReq(BaseModel):
    content: str


@router.put("/knowledge/{filename}")
async def update_document(filename: str, req: UpdateContentReq):
    """更新文档内容并重新入库（先清理旧向量）"""
    safe_name = _safe_filename(filename)
    file_path = os.path.join(KNOWLEDGE_DIR, safe_name)
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail=f"文件不存在: {safe_name}")

    # 先删除旧向量
    delete_by_source(safe_name)

    # 写入新内容
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(req.content)

    # 重新入库
    doc = load_single_document(file_path)
    if doc:
        chunks = split_documents([doc])
        count = add_documents(chunks)
        rebuild_bm25()
        return {"message": f"文档 {safe_name} 已更新并重新入库", "chunks_count": count}

    return {"message": f"文档 {safe_name} 已更新"}


@router.post("/knowledge/toggle/{filename}")
async def toggle_document(filename: str):
    """切换文档启用/禁用状态"""
    safe_name = _safe_filename(filename)
    file_path = os.path.join(KNOWLEDGE_DIR, safe_name)
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail=f"文件不存在: {safe_name}")

    enabled = toggle_file(safe_name)
    return {"message": f"文档 {safe_name} 已{'启用' if enabled else '禁用'}", "enabled": enabled}


@router.delete("/knowledge/{filename}")
async def delete_document(filename: str):
    """删除已入库的知识库文档（同时清理向量）"""
    safe_name = _safe_filename(filename)
    file_path = os.path.join(KNOWLEDGE_DIR, safe_name)
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail=f"文件不存在: {safe_name}")

    # 先删除向量
    delete_by_source(safe_name)
    # 再删除文件
    os.remove(file_path)
    rebuild_bm25()

    return {"message": f"文件 {safe_name} 已删除"}


@router.post("/knowledge/rebuild")
async def rebuild_knowledge(background_tasks: BackgroundTasks = None):
    """重建整个知识库向量索引（异步）"""
    if _rebuild_status["running"]:
        return {"message": "重建正在进行中，请稍后", "status": "running"}
    background_tasks.add_task(_rebuild_all)
    return {"message": "正在后台重建索引...", "status": "processing"}


@router.get("/knowledge/rebuild/status")
async def rebuild_status():
    """查询重建状态"""
    return _rebuild_status
