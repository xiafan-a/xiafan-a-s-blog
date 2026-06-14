from sqlalchemy import create_engine, Column, Integer, String, Text, DateTime, Boolean, Float, ForeignKey, JSON, ARRAY
from sqlalchemy.ext.declarative import declarative_base
from pgvector.sqlalchemy import Vector
from sqlalchemy.orm import sessionmaker, relationship
import os
from dotenv import load_dotenv
from datetime import datetime

# 加载环境变量
load_dotenv()

# 获取数据库URL
DATABASE_URL = os.getenv("DATABASE_URL", "")

# 创建数据库引擎
engine = create_engine(DATABASE_URL)

# 创建会话工厂
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# 创建基类
Base = declarative_base()

# 数据库模型类
class KnowledgeBaseModel(Base):
    __tablename__ = "knowledge_bases"
    
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    system_prompt = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    is_deleted = Column(Integer, default=0)
    
    # 关系
    files = relationship("KnowledgeFileModel", back_populates="knowledge_base")
    chunks = relationship("KnowledgeChunkModel", back_populates="knowledge_base")
    messages = relationship("ConversationMessageModel", back_populates="knowledge_base")

class KnowledgeFileModel(Base):
    __tablename__ = "knowledge_files"
    
    id = Column(Integer, primary_key=True, index=True)
    knowledge_base_id = Column(Integer, ForeignKey("knowledge_bases.id"), nullable=False)
    file_name = Column(String(512), nullable=False)
    file_size = Column(Integer, nullable=False)
    file_type = Column(String(100), nullable=True)
    file_hash = Column(String(64), nullable=False)
    indexing_method = Column(String(50), default="semantic")
    status = Column(String(20), default="pending")
    error_message = Column(Text, nullable=True)
    file_metadata = Column(JSON, default=dict)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    is_deleted = Column(Integer, default=0)
    
    # 关系
    knowledge_base = relationship("KnowledgeBaseModel", back_populates="files")
    chunks = relationship("KnowledgeChunkModel", back_populates="knowledge_file")

class KnowledgeChunkModel(Base):
    __tablename__ = "knowledge_chunks"
    
    id = Column(Integer, primary_key=True, index=True)
    knowledge_base_id = Column(Integer, ForeignKey("knowledge_bases.id"), nullable=False)
    knowledge_file_id = Column(Integer, ForeignKey("knowledge_files.id"), nullable=True)
    chunk_index = Column(Integer, nullable=False)
    content = Column(Text, nullable=False)
    embedding = Column(Vector(768), nullable=True)
    chunk_metadata = Column(JSON, default=dict)
    indexing_method = Column(String(50), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    is_deleted = Column(Integer, default=0)
    
    # 关系
    knowledge_base = relationship("KnowledgeBaseModel", back_populates="chunks")
    knowledge_file = relationship("KnowledgeFileModel", back_populates="chunks")

class ConversationMessageModel(Base):
    __tablename__ = "conversation_messages"
    
    id = Column(Integer, primary_key=True, index=True)
    knowledge_base_id = Column(Integer, ForeignKey("knowledge_bases.id"), nullable=False)
    role = Column(String(20), nullable=False)
    content = Column(Text, nullable=False)
    session_id = Column(Integer, nullable=True)
    parent_message_id = Column(Integer, nullable=True)
    context_window = Column(Integer, default=10)
    context_summary = Column(Text, nullable=True)
    sources = Column(JSON, default=list)
    token_usage = Column(JSON, default=dict)
    feedback = Column(Integer, nullable=True)
    message_metadata = Column(JSON, default=dict)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    is_deleted = Column(Integer, default=0)
    
    # 关系
    knowledge_base = relationship("KnowledgeBaseModel", back_populates="messages")

class ConversationSessionModel(Base):
    __tablename__ = "conversation_session"

    id = Column(Integer, primary_key=True, index=True)
    knowledge_base_id = Column(Integer, ForeignKey("knowledge_bases.id"), nullable=False, index=True)
    title = Column(String(255))
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    is_deleted = Column(Integer, default=0)


class GuessWordModel(Base):
    __tablename__ = "guess_words"

    id = Column(Integer, primary_key=True, index=True)
    word = Column(String(255), nullable=False)
    hint = Column(Text, nullable=True)
    difficulty = Column(Integer, default=1)
    is_passed = Column(Boolean, default=False)
    pass_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)
    embedding = Column(Vector(768), nullable=True)


class GuessRecordModel(Base):
    __tablename__ = "guess_records"

    id = Column(Integer, primary_key=True, index=True)
    guess_word_id = Column(Integer, ForeignKey("guess_words.id"), nullable=False, index=True)
    guess = Column(String(255), nullable=False)
    similarity = Column(Float, nullable=False)
    is_correct = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    # 关系
    guess_word = relationship("GuessWordModel", backref="records")


# 创建所有表
Base.metadata.create_all(bind=engine)

# 依赖项：获取数据库会话
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
