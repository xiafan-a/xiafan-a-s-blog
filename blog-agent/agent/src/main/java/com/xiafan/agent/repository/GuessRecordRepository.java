package com.xiafan.agent.repository;

import com.xiafan.agent.entity.GuessRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class GuessRecordRepository extends BaseRepository {

    public GuessRecordRepository(DataSource dataSource) {
        super(dataSource);
    }

    public static final RowMapper<GuessRecord> ROW_MAPPER = (rs, rowNum) -> {
        GuessRecord r = new GuessRecord();
        r.setId(rs.getInt("id"));
        r.setGuessWordId(rs.getInt("guess_word_id"));
        r.setGuess(rs.getString("guess"));
        r.setSimilarity(rs.getDouble("similarity"));
        r.setCorrect(rs.getBoolean("is_correct"));
        r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return r;
    };

    public GuessRecord insert(int guessWordId, String guess, double similarity, boolean isCorrect) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO guess_records (guess_word_id, guess, similarity, is_correct, created_at) "
                            + "VALUES (?, ?, ?, ?, now())",
                    new String[]{"id"});
            ps.setInt(1, guessWordId);
            ps.setString(2, guess);
            ps.setDouble(3, similarity);
            ps.setBoolean(4, isCorrect);
            return ps;
        }, keyHolder);
        return found(keyHolder.getKey().intValue());
    }

    private GuessRecord found(int id) {
        return jdbc.query("SELECT * FROM guess_records WHERE id = ?", ROW_MAPPER, id).get(0);
    }

    public List<GuessRecord> findByWordId(int wordId, int skip, int limit) {
        return jdbc.query(
                "SELECT * FROM guess_records WHERE guess_word_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, wordId, limit, skip);
    }
}