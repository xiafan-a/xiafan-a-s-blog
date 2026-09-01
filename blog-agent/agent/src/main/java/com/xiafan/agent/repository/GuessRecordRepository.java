package com.xiafan.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiafan.agent.entity.GuessRecord;

import java.util.List;

public interface GuessRecordRepository extends BaseMapper<GuessRecord> {

    default GuessRecord insert(int guessWordId, String guess, double similarity, boolean isCorrect) {
        GuessRecord record = new GuessRecord();
        record.setGuessWordId(guessWordId);
        record.setGuess(guess);
        record.setSimilarity(similarity);
        record.setCorrect(isCorrect);
        insert(record);
        return selectById(record.getId());
    }

    default List<GuessRecord> findByWordId(int wordId, int skip, int limit) {
        return selectList(new LambdaQueryWrapper<GuessRecord>()
                .eq(GuessRecord::getGuessWordId, wordId)
                .orderByDesc(GuessRecord::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + skip));
    }
}
