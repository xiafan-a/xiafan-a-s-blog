package com.xiafan.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiafan.agent.entity.GuessWord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class GuessWordRepository extends BaseRepository {

    public GuessWordRepository(DataSource dataSource) {
        super(dataSource);
    }

    public static final RowMapper<GuessWord> ROW_MAPPER = (rs, rowNum) -> {
        GuessWord w = new GuessWord();
        w.setId(rs.getInt("id"));
        w.setWord(rs.getString("word"));
        w.setHint(rs.getString("hint"));
        w.setDifficulty(rs.getInt("difficulty"));
        w.setPassed(rs.getBoolean("is_passed"));
        w.setPassCount(rs.getInt("pass_count"));
        w.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        w.setEmbedding(fromJsonList(rs.getString("embedding"), new TypeReference<>() {
        }));
        return w;
    };

    public GuessWord insert(String word, String hint, int difficulty) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO guess_words (word, hint, difficulty, is_passed, pass_count, created_at) "
                            + "VALUES (?, ?, ?, FALSE, 0, now())",
                    new String[]{"id"});
            ps.setString(1, word);
            ps.setString(2, hint);
            ps.setInt(3, difficulty);
            return ps;
        }, keyHolder);
        return findById(keyHolder.getKey().intValue()).orElseThrow();
    }

    public Optional<GuessWord> findById(int id) {
        return jdbc.query("SELECT * FROM guess_words WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public List<GuessWord> findAll() {
        return jdbc.query(
                "SELECT id, word, hint, difficulty, is_passed, pass_count, created_at, "
                        + "CASE WHEN embedding IS NULL THEN NULL ELSE embedding END AS embedding "
                        + "FROM guess_words ORDER BY created_at DESC",
                ROW_MAPPER);
    }

    public int updateEmbedding(int id, List<Double> embedding) {
        return jdbc.update("UPDATE guess_words SET embedding = ?::jsonb WHERE id = ?",
                toJson(embedding), id);
    }

    public int markAsPassed(int id) {
        return jdbc.update(
                "UPDATE guess_words SET is_passed = TRUE, pass_count = pass_count + 1 WHERE id = ?", id);
    }

    public int countAll() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM guess_words", Integer.class);
        return c == null ? 0 : c;
    }

    public int countPassed() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM guess_words WHERE is_passed = TRUE", Integer.class);
        return c == null ? 0 : c;
    }
}