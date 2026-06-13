from fastapi import APIRouter, HTTPException, Depends
from typing import List, Optional
from pydantic import BaseModel
from sqlalchemy.orm import Session

from database import get_db
from service.guessWordService import guess_word_service
from service.guessRecordService import guess_record_service
from entity.GuessWord import GuessWord
from entity.GuessRecord import GuessRecord
from util.response import ApiResponse

router = APIRouter()


class GuessWordCreate(BaseModel):
    word: str
    hint: Optional[str] = None
    difficulty: int = 1


class GuessRequest(BaseModel):
    guess: str


@router.post("/guess-words", response_model=ApiResponse[GuessWord])
def create_guess_word(
    word: GuessWordCreate,
    db: Session = Depends(get_db)
):
    """创建目标字"""
    result = guess_word_service.create_guess_word(
        db=db,
        word=word.word,
        hint=word.hint,
        difficulty=word.difficulty
    )
    return ApiResponse(code="200", data=result)


@router.get("/guess-words/{word_id}", response_model=ApiResponse[GuessWord])
def get_guess_word(
    word_id: int,
    db: Session = Depends(get_db)
):
    """根据ID获取目标字"""
    result = guess_word_service.get_guess_word_by_id(db, word_id)
    if not result:
        raise HTTPException(status_code=404, detail="目标字不存在")
    return ApiResponse(code="200", data=result)


@router.get("/guess-words", response_model=ApiResponse[List[GuessWord]])
def list_guess_words(
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db)
):
    """列出目标字列表"""
    result = guess_word_service.list_guess_words(db, skip, limit)
    return ApiResponse(code="200", data=result)


@router.post("/guess-words/{word_id}/guess", response_model=ApiResponse[GuessRecord])
def make_guess(
    word_id: int,
    request: GuessRequest,
    db: Session = Depends(get_db)
):
    """提交猜测"""
    result = guess_record_service.create_record(db, word_id, request.guess)
    if not result:
        raise HTTPException(status_code=404, detail="目标字不存在")
    return ApiResponse(code="200", data=result)


@router.get("/guess-words/{word_id}/records", response_model=ApiResponse[List[GuessRecord]])
def get_guess_records(
    word_id: int,
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db)
):
    """获取目标字的猜测记录"""
    result = guess_record_service.get_records_by_word_id(db, word_id, skip, limit)
    return ApiResponse(code="200", data=result)


class GuessWordStats(BaseModel):
    total: int
    passed: int


@router.get("/guess-words/stats/count", response_model=ApiResponse[GuessWordStats])
def get_guess_word_stats(
    db: Session = Depends(get_db)
):
    """获取关卡统计数据"""
    total = guess_word_service.get_total_count(db)
    passed = guess_word_service.get_passed_count(db)
    return ApiResponse(code="200", data=GuessWordStats(total=total, passed=passed))