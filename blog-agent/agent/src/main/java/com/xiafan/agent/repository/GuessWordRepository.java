package com.xiafan.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiafan.agent.entity.GuessWord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

public interface GuessWordRepository extends BaseMapper<GuessWord> {

    default GuessWord insert(String word, String hint, int difficulty) {
        GuessWord guessWord = new GuessWord();
        guessWord.setWord(word);
        guessWord.setHint(hint);
        guessWord.setDifficulty(difficulty);
        insert(guessWord);
        return selectById(guessWord.getId());
    }

    default Optional<GuessWord> findById(int id) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<GuessWord>()
                .eq(GuessWord::getId, id)));
    }

    default List<GuessWord> findAll() {
        return selectList(new LambdaQueryWrapper<GuessWord>()
                .orderByDesc(GuessWord::getCreatedAt));
    }

    @Update("""
            UPDATE guess_words
            SET embedding = #{embedding,jdbcType=OTHER,typeHandler=com.xiafan.agent.repository.PostgresJsonTypeHandler}
            WHERE id = #{id}
            """)
    int updateEmbedding(@Param("id") int id, @Param("embedding") List<Double> embedding);

    @Update("""
            UPDATE guess_words
            SET is_passed = TRUE, pass_count = pass_count + 1
            WHERE id = #{id}
            """)
    int markAsPassed(@Param("id") int id);

    default int countAll() {
        return Math.toIntExact(selectCount(new LambdaQueryWrapper<GuessWord>()));
    }

    default int countPassed() {
        return Math.toIntExact(selectCount(new LambdaQueryWrapper<GuessWord>()
                .eq(GuessWord::isPassed, true)));
    }
}
