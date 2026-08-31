package com.xiafan.agent.controller;

import com.xiafan.agent.common.ApiResponse;
import com.xiafan.agent.common.BusinessException;
import com.xiafan.agent.entity.GuessRecord;
import com.xiafan.agent.entity.GuessWord;
import com.xiafan.agent.service.GuessRecordService;
import com.xiafan.agent.service.GuessWordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors fastApiProject/api/guessWord.py.
 */
@RestController
@RequestMapping("/api/v1/guess-words")
public class GuessWordController {

    private final GuessWordService guessWordService;
    private final GuessRecordService guessRecordService;

    public GuessWordController(GuessWordService guessWordService, GuessRecordService guessRecordService) {
        this.guessWordService = guessWordService;
        this.guessRecordService = guessRecordService;
    }

    public record GuessWordCreate(String word, String hint, Integer difficulty) {
    }

    public record GuessRequest(String guess) {
    }

    @PostMapping
    public GuessWord createGuessWord(@RequestBody GuessWordCreate word) {
        int difficulty = word.difficulty() == null ? 1 : word.difficulty();
        return guessWordService.createGuessWord(word.word(), word.hint(), difficulty);
    }

    @GetMapping("/{wordId}")
    public GuessWord getGuessWord(@PathVariable int wordId) {
        return guessWordService.getGuessWordById(wordId)
                .orElseThrow(() -> new BusinessException(404, "目标字不存在"));
    }

    @GetMapping
    public ApiResponse<List<GuessWord>> listGuessWords(@RequestParam(defaultValue = "0") int skip,
                                                       @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(guessWordService.listGuessWords(skip, limit));
    }

    @PostMapping("/{wordId}/guess")
    public GuessRecord makeGuess(@PathVariable int wordId, @RequestBody GuessRequest request) {
        return guessRecordService.createRecord(wordId, request.guess())
                .orElseThrow(() -> new BusinessException(404, "目标字不存在"));
    }

    @GetMapping("/{wordId}/records")
    public ApiResponse<List<GuessRecord>> getGuessRecords(@PathVariable int wordId,
                                                          @RequestParam(defaultValue = "0") int skip,
                                                          @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(guessRecordService.getRecordsByWordId(wordId, skip, limit));
    }

    @GetMapping("/stats/count")
    public Map<String, Integer> getGuessWordStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("total", guessWordService.getTotalCount());
        stats.put("passed", guessWordService.getPassedCount());
        return stats;
    }
}