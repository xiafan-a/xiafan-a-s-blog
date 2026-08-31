package com.xiafan.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiafan.agent.config.AppProperties;
import com.xiafan.agent.entity.GuessWord;
import com.xiafan.agent.repository.GuessWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mirrors guessWordService.py (Redis-cached list, embedding on create, pass marking). */
@Service
public class GuessWordService {

    private static final Logger log = LoggerFactory.getLogger(GuessWordService.class);

    private final GuessWordRepository repository;
    private final EmbeddingService embeddingService;
    private final RedisService redisService;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public GuessWordService(GuessWordRepository repository, EmbeddingService embeddingService,
                            RedisService redisService, AppProperties props, ObjectMapper objectMapper) {
        this.repository = repository;
        this.embeddingService = embeddingService;
        this.redisService = redisService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public GuessWord createGuessWord(String word, String hint, int difficulty) {
        GuessWord created = repository.insert(word, hint, difficulty);
        try {
            List<Double> embedding = embeddingService.encodeSingle(word);
            repository.updateEmbedding(created.getId(), embedding);
        } catch (Exception e) {
            log.warn("embedding generation failed for guess word {}: {}", word, e.getMessage());
        }
        redisService.invalidateGuessWordsCache();
        return created;
    }

    public Optional<GuessWord> getGuessWordById(int wordId) {
        return repository.findById(wordId);
    }

    /** Paginated list with Redis caching; word is masked as its length and embedding omitted (mirrors Python). */
    public List<GuessWord> listGuessWords(int skip, int limit) {
        Optional<String> cached = redisService.getCachedGuessWordsList();
        if (cached.isPresent()) {
            try {
                List<Map<String, Object>> allWords = objectMapper.readValue(cached.get(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                        });
                List<Map<String, Object>> paginated = paginate(allWords, skip, limit);
                List<GuessWord> result = new ArrayList<>();
                for (Map<String, Object> w : paginated) {
                    result.add(toMaskedEntity(w));
                }
                return result;
            } catch (Exception e) {
                log.warn("guess-words cache parse failed, falling back to DB: {}", e.getMessage());
            }
        }
        List<GuessWord> dbWords = repository.findAll();
        List<Map<String, Object>> cacheData = new ArrayList<>();
        for (GuessWord w : dbWords) {
            cacheData.add(toCacheEntry(w));
        }
        try {
            redisService.cacheGuessWordsList(objectMapper.writeValueAsString(cacheData),
                    props.getGuess().getListCacheTtl());
        } catch (Exception e) {
            log.warn("guess-words cache write failed: {}", e.getMessage());
        }
        if (dbWords.size() > 0) {
            List<Map<String, Object>> paginated = paginate(cacheData, skip, limit);
            List<GuessWord> result = new ArrayList<>();
            for (Map<String, Object> w : paginated) {
                result.add(toMaskedEntity(w));
            }
            return result;
        }
        return List.of();
    }

    public Optional<GuessWord> markAsPassed(int wordId) {
        if (repository.markAsPassed(wordId) == 0) {
            return Optional.empty();
        }
        redisService.invalidateGuessWordsCache();
        return repository.findById(wordId);
    }

    public int getTotalCount() {
        return repository.countAll();
    }

    public int getPassedCount() {
        return repository.countPassed();
    }

    public int similarityCacheTtl() {
        return props.getGuess().getSimilarityCacheTtl();
    }

    private Map<String, Object> toCacheEntry(GuessWord w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("word", w.getWord());
        m.put("hint", w.getHint());
        m.put("difficulty", w.getDifficulty());
        m.put("is_passed", w.isPassed());
        m.put("pass_count", w.getPassCount());
        m.put("embedding", List.of());
        m.put("created_at", String.valueOf(w.getCreatedAt()));
        return m;
    }

    private static List<Map<String, Object>> paginate(List<Map<String, Object>> all, int skip, int limit) {
        int from = Math.min(skip, all.size());
        int to = Math.min(skip + limit, all.size());
        return all.subList(from, to);
    }

    private static GuessWord toMaskedEntity(Map<String, Object> w) {
        Object word = w.get("word");
        String masked = String.valueOf(word == null ? "" : word.toString().length());
        GuessWord e = new GuessWord();
        e.setId(((Number) w.getOrDefault("id", 0)).intValue());
        e.setWord(masked);
        e.setHint((String) w.get("hint"));
        e.setDifficulty(((Number) w.getOrDefault("difficulty", 1)).intValue());
        e.setPassed(Boolean.TRUE.equals(w.get("is_passed")));
        e.setPassCount(((Number) w.getOrDefault("pass_count", 0)).intValue());
        e.setEmbedding(List.of());
        return e;
    }
}