from sqlalchemy.orm import Session, defer
from sqlalchemy import func
from typing import List, Optional
from datetime import datetime
import json

from entity.GuessWord import GuessWord
from database import GuessWordModel
from service.embeddingService import embedding_service
from service.redisService import redis_service


class GuessWordService:
    def __init__(self):
        pass

    def create_guess_word(self, db: Session, word: str, hint: Optional[str] = None,
                          difficulty: int = 1) -> GuessWord:
        """创建目标字"""
        db_word = GuessWordModel(
            word=word,
            hint=hint,
            difficulty=difficulty,
            is_passed=False,
            pass_count=0,
            created_at=datetime.now()
        )
        db.add(db_word)
        db.commit()
        db.refresh(db_word)

        # 生成并保存向量
        embedding_vec = embedding_service.encode_single(word)
        db_word.embedding = embedding_vec
        db.commit()
        db.refresh(db_word)

        # 使缓存失效
        redis_service.invalidate_guess_words_cache()

        return self._to_pydantic(db_word)

    def get_guess_word_by_id(self, db: Session, word_id: int) -> Optional[GuessWord]:
        """根据ID获取目标字"""
        db_word = db.query(GuessWordModel).filter(
            GuessWordModel.id == word_id
        ).first()

        if not db_word:
            return None

        return self._to_pydantic(db_word)

    def list_guess_words(self, db: Session, skip: int = 0, limit: int = 10) -> List[GuessWord]:
        """列出目标字列表（使用Redis缓存）"""
        # 尝试从Redis获取缓存
        cached_data = redis_service.get_cached_guess_words_list()
        if cached_data:
            try:
                all_words = json.loads(cached_data)
                paginated = all_words[skip:skip + limit]
                for i in paginated:
                    i.word = str(len(i.word))
                    i.embedding = []
                return [GuessWord(**w) for w in paginated]
            except Exception:
                pass

        # 缓存未命中，从数据库查询（排除embedding字段以提升性能）
        db_words = db.query(GuessWordModel).options(
            defer(GuessWordModel.embedding)
        ).order_by(
            GuessWordModel.created_at.desc()
        ).all()
        res = [self._to_pydantic(w, include_embedding=False) for w in db_words]
        for i in res:
            i.word = str(len(i.word))
            i.embedding = []

        # 缓存结果
        cache_data = [self._to_pydantic(w, include_embedding=False).model_dump(mode='json') for w in db_words]
        redis_service.cache_guess_words_list(json.dumps(cache_data, default=str))

        return res[skip:skip + limit]

    def mark_as_passed(self, db: Session, word_id: int) -> Optional[GuessWord]:
        """标记目标字为已通过"""
        db_word = db.query(GuessWordModel).filter(
            GuessWordModel.id == word_id
        ).first()

        if not db_word:
            return None

        db_word.is_passed = True
        db_word.pass_count = db_word.pass_count + 1
        db.commit()
        db.refresh(db_word)

        # 使缓存失效
        redis_service.invalidate_guess_words_cache()

        return self._to_pydantic(db_word)

    def get_total_count(self, db: Session) -> int:
        """获取目标字总数"""
        return db.query(func.count(GuessWordModel.id)).scalar()

    def get_passed_count(self, db: Session) -> int:
        """获取已通过的目标字数量"""
        return db.query(GuessWordModel).filter(GuessWordModel.is_passed == True).count()

    def _to_pydantic(self, db_word: GuessWordModel, include_embedding: bool = True) -> GuessWord:
        """ORM模型转Pydantic实体"""
        return GuessWord(
            id=db_word.id,
            word=db_word.word,
            hint=db_word.hint,
            difficulty=db_word.difficulty,
            is_passed=db_word.is_passed,
            pass_count=db_word.pass_count,
            created_at=db_word.created_at,
            embedding=db_word.embedding if include_embedding else []
        )


# 全局单例实例
guess_word_service = GuessWordService()
