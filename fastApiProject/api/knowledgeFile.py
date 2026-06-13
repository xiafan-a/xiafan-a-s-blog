from fastapi import APIRouter, HTTPException, UploadFile, File, Form, Depends, BackgroundTasks
from sqlalchemy.orm import Session
from typing import List, Optional
import hashlib
import os
from pathlib import Path

from service.knowledgeFileService import knowledge_file_service
from service.knowledgeChunkService import knowledge_chunk_service
from service.fileProcessingService import file_processor, text_chunker
from entity.KnowledgeFile import KnowledgeFile
from service.minioService import minio_service
from util.response import ApiResponse
from database import get_db
from config.settings import UPLOAD_DIR, SUPPORTED_FILE_TYPES

router = APIRouter()


def process_file_task(file_id: int, file_path: str, knowledge_base_id: int):
    """后台任务：处理文件并向量化"""
    import tempfile
    from database import SessionLocal
    db = SessionLocal()
    try:
        # 更新状态为处理中
        knowledge_file_service.update_file_status(db, file_id, "processing")
        # 从MinIO下载文件内容
        file_bytes = minio_service.download_file(file_path)
        if file_bytes is None:
            raise ValueError(f"从MinIO下载文件失败: {file_path}")

        # 根据文件扩展名处理不同类型文件
        ext = Path(file_path).suffix.lower()
        if ext in [".txt", ".md"]:
            # 文本文件直接解码
            encodings = ['utf-8', 'gbk', 'gb2312', 'utf-16']
            content = None
            for encoding in encodings:
                try:
                    content = file_bytes.decode(encoding)
                    break
                except UnicodeDecodeError:
                    continue
            if content is None:
                raise ValueError(f"无法解码文本文件: {file_path}")
        elif ext == ".pdf":
            # PDF文件写入临时文件后读取
            with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
                tmp.write(file_bytes)
                tmp_path = tmp.name
            try:
                content = file_processor.read_file(tmp_path)
            finally:
                os.unlink(tmp_path)
        elif ext == ".docx":
            # DOCX文件写入临时文件后读取
            with tempfile.NamedTemporaryFile(suffix=".docx", delete=False) as tmp:
                tmp.write(file_bytes)
                tmp_path = tmp.name
            try:
                content = file_processor.read_file(tmp_path)
            finally:
                os.unlink(tmp_path)
        else:
            raise ValueError(f"不支持的文件类型: {ext}")
        # 分块
        chunks_data = text_chunker.chunk_text(content, {"file_id": file_id, "source": os.path.basename(file_path)})
        # 向量化并存储
        knowledge_chunk_service.batch_create_chunks(
            db=db,
            knowledge_base_id=knowledge_base_id,
            chunks_data=chunks_data,
            knowledge_file_id=file_id
        )
        # 更新状态为完成
        knowledge_file_service.update_file_status(db, file_id, "completed")

    except Exception as e:
        knowledge_file_service.update_file_status(
            db, file_id, "failed", str(e)
        )
    finally:
        db.close()


@router.post("/knowledge-files", response_model=ApiResponse[KnowledgeFile])
async def create_knowledge_file(
        background_tasks: BackgroundTasks,
        knowledge_base_id: int = Form(...),
        file: UploadFile = File(...),
        db: Session = Depends(get_db)
):
    """上传知识文件"""
    # 检查文件类型
    file_ext = Path(file.filename).suffix.lower()
    if file_ext not in SUPPORTED_FILE_TYPES:
        raise HTTPException(status_code=400, detail=f"不支持的文件类型: {file_ext}")

    # 读取文件内容
    content = await file.read()
    file_hash = hashlib.sha256(content).hexdigest()

    # 检查是否已存在相同文件
    existing_file = knowledge_file_service.get_file_by_hash(db, knowledge_base_id, file_hash)
    if existing_file:
        raise HTTPException(status_code=400, detail="该文件已存在于知识库中")

    # 保存文件
    result = await minio_service.upload_file_with_original_byte(content, file.filename, file.content_type)
    file_name = result[1]

    # 创建数据库记录
    knowledge_file = knowledge_file_service.create_knowledge_file(
        db=db,
        knowledge_base_id=knowledge_base_id,
        file_name=file.filename,
        file_size=len(content),
        file_type=file.content_type,
        file_hash=file_hash,
        file_metadata={"path": file_name}
    )

    # 启动后台处理任务
    background_tasks.add_task(
        process_file_task,
        knowledge_file.id,
        file_name,
        knowledge_base_id
    )

    return ApiResponse(code="200", data=knowledge_file)


@router.get("/knowledge-files/{file_id}", response_model=ApiResponse[KnowledgeFile])
def get_knowledge_file(
        file_id: int,
        db: Session = Depends(get_db)
):
    """根据ID获取知识文件"""
    file = knowledge_file_service.get_knowledge_file_by_id(db, file_id)
    if not file:
        raise HTTPException(status_code=404, detail="文件不存在")
    return ApiResponse(code="200", data=file)


@router.get("/knowledge-bases/{kb_id}/files", response_model=ApiResponse[List[KnowledgeFile]])
def get_files_by_knowledge_base(
        kb_id: int,
        skip: int = 0,
        limit: int = 100,
        db: Session = Depends(get_db)
):
    """根据知识库ID获取文件列表"""
    result = knowledge_file_service.get_files_by_knowledge_base(db, kb_id, skip, limit)
    return ApiResponse(code="200", data=result)


@router.put("/knowledge-files/{file_id}/status", response_model=ApiResponse[KnowledgeFile])
def update_file_status(
        file_id: int,
        status: str,
        error_message: Optional[str] = None,
        db: Session = Depends(get_db)
):
    """更新文件状态"""
    updated_file = knowledge_file_service.update_file_status(
        db=db,
        file_id=file_id,
        status=status,
        error_message=error_message
    )
    if not updated_file:
        raise HTTPException(status_code=404, detail="文件不存在")
    return ApiResponse(code="200", data=updated_file)


@router.delete("/knowledge-files/{file_id}", response_model=ApiResponse[dict])
def delete_knowledge_file(
        file_id: int,
        db: Session = Depends(get_db)
):
    """删除知识文件"""
    # 获取文件信息
    file = knowledge_file_service.get_knowledge_file_by_id(db, file_id)
    if not file:
        raise HTTPException(status_code=404, detail="文件不存在")

    # 删除关联的分块
    knowledge_chunk_service.delete_chunks_by_file(db, file_id)

    # 软删除文件记录
    success = knowledge_file_service.soft_delete_file(db, file_id)
    if not success:
        raise HTTPException(status_code=404, detail="文件不存在")

    return ApiResponse(code="200", data={"message": "文件删除成功"})
