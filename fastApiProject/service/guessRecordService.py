from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import datetime

from entity.GuessRecord import GuessRecord
from database import GuessRecordModel, GuessWordModel
from service.rerankService import rerank_service
from service.redisService import redis_service


class GuessRecordService:
    def __init__(self):
        pass

    def create_record(self, db: Session, guess_word_id: int, guess: str) -> Optional[GuessRecord]:
        """
        创建猜测记录
        1. 先检查 Redis 缓存是否已有相同猜测的结果
        2. 如果没有，使用数据库中预存的向量计算相似度
        3. 保存到数据库并缓存到 Redis
        """
        # 获取目标词
        db_word = db.query(GuessWordModel).filter(
            GuessWordModel.id == guess_word_id
        ).first()

        if not db_word:
            return None

        target_word = db_word.word

        # 检查 Redis 缓存
        cached = self._get_cached_similarity(guess_word_id, guess)

        # 判断是否猜中（中文完全一致才为猜测正确）
        is_correct = (guess == target_word)
        similarity = 0
        if not is_correct:
            if cached is not None:
                similarity = cached
            else:
                # 使用预存的向量计算相似度（减少一次 API 调用）
                if db_word.embedding is not None:
                    similarity = rerank_service.calculate_similarity_from_embedding(db_word.embedding, guess)
                else:
                    # 降级：使用原始方法
                    similarity = rerank_service.calculate_similarity_by_embedding(target_word, guess)
                # 缓存结果
                self._cache_similarity(guess_word_id, guess, similarity)

        else:
            similarity = 1.0
        # 如果猜中，更新目标字状态
        if is_correct and not db_word.is_passed:
            db_word.is_passed = True
            db_word.pass_count = db_word.pass_count + 1

        # 创建记录
        db_record = GuessRecordModel(
            guess_word_id=guess_word_id,
            guess=guess,
            similarity=similarity,
            is_correct=is_correct,
            created_at=datetime.now()
        )
        db.add(db_record)
        db.commit()
        db.refresh(db_record)

        return self._to_pydantic(db_record)

    def get_records_by_word_id(self, db: Session, word_id: int,
                               skip: int = 0, limit: int = 100) -> List[GuessRecord]:
        """获取某目标字的猜测记录"""
        db_records = db.query(GuessRecordModel).filter(
            GuessRecordModel.guess_word_id == word_id
        ).order_by(
            GuessRecordModel.created_at.desc()
        ).offset(skip).limit(limit).all()

        return [self._to_pydantic(r) for r in db_records]

    def _get_cached_similarity(self, word_id: int, guess: str) -> Optional[float]:
        """从 Redis 获取缓存的相似度"""
        return redis_service.get_cached_similarity(word_id, guess)

    def _cache_similarity(self, word_id: int, guess: str, similarity: float):
        """缓存相似度到 Redis（默认24小时过期）"""
        redis_service.cache_guess_similarity(word_id, guess, similarity)

    def _to_pydantic(self, db_record: GuessRecordModel) -> GuessRecord:
        """ORM模型转Pydantic实体"""
        return GuessRecord(
            id=db_record.id,
            guess_word_id=db_record.guess_word_id,
            guess=db_record.guess,
            similarity=db_record.similarity,
            is_correct=db_record.is_correct,
            created_at=db_record.created_at
        )


# 全局单例实例
guess_record_service = GuessRecordService()
