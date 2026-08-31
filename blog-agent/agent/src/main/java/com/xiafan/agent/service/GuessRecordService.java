package com.xiafan.agent.service;

import com.xiafan.agent.entity.GuessWord;
import com.xiafan.agent.entity.GuessRecord;
import com.xiafan.agent.repository.GuessRecordRepository;
import com.xiafan.agent.repository.GuessWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Mirrors guessRecordService.py (cached similarity, embedding-based scoring, pass marking on correct guess). */
@Service
public class GuessRecordService {

    private static final Logger log = LoggerFactory.getLogger(GuessRecordService.class);

    private final GuessRecordRepository recordRepository;
    private final GuessWordRepository wordRepository;
    private final RerankService rerankService;
    private final RedisService redisService;
    private final GuessWordService guessWordService;

    public GuessRecordService(GuessRecordRepository recordRepository, GuessWordRepository wordRepository,
                              RerankService rerankService, RedisService redisService, GuessWordService guessWordService) {
        this.recordRepository = recordRepository;
        this.wordRepository = wordRepository;
        this.rerankService = rerankService;
        this.redisService = redisService;
        this.guessWordService = guessWordService;
    }

    public Optional<GuessRecord> createRecord(int guessWordId, String guess) {
        Optional<GuessWord> wordOpt = wordRepository.findById(guessWordId);
        if (wordOpt.isEmpty()) {
            return Optional.empty();
        }
        GuessWord dbWord = wordOpt.get();
        String targetWord = dbWord.getWord();

        Optional<Double> cached = redisService.getCachedSimilarity(guessWordId, guess);
        boolean isCorrect = guess.equals(targetWord);
        double similarity;
        if (isCorrect) {
            similarity = 1.0;
        } else if (cached.isPresent()) {
            similarity = cached.get();
        } else {
            similarity = computeSimilarity(dbWord, targetWord, guess);
            redisService.cacheGuessSimilarity(guessWordId, guess, similarity,
                    guessWordService.similarityCacheTtl());
        }

        if (isCorrect && !dbWord.isPassed()) {
            guessWordService.markAsPassed(guessWordId);
        }

        GuessRecord record = recordRepository.insert(guessWordId, guess, similarity, isCorrect);
        return Optional.of(record);
    }

    public List<GuessRecord> getRecordsByWordId(int wordId, int skip, int limit) {
        return recordRepository.findByWordId(wordId, skip, limit);
    }

    private double computeSimilarity(GuessWord dbWord, String targetWord, String guess) {
        try {
            if (dbWord.getEmbedding() != null && !dbWord.getEmbedding().isEmpty()) {
                return rerankService.calculateSimilarityFromEmbedding(dbWord.getEmbedding(), guess);
            }
            return rerankService.calculateSimilarityByEmbedding(targetWord, guess);
        } catch (Exception e) {
            log.warn("similarity computation failed: {}", e.getMessage());
            return 0.0;
        }
    }
}