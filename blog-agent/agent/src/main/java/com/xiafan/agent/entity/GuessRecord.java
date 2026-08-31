package com.xiafan.agent.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

/** guess_records */
@Data
public class GuessRecord {
    private Integer id;
    private Integer guessWordId;
    private String guess;
    private Double similarity;

    @Getter(onMethod_ = @JsonProperty("is_correct"))
    private boolean isCorrect = false;

    private LocalDateTime createdAt;
}