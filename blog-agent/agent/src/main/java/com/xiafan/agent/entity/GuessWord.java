package com.xiafan.agent.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** guess_words */
@Data
public class GuessWord {
    private Integer id;
    private String word;
    private String hint;
    private Integer difficulty = 1;

    @Getter(onMethod_ = @JsonProperty("is_passed"))
    private boolean isPassed = false;

    private Integer passCount = 0;
    private LocalDateTime createdAt;
    private List<Double> embedding;
}