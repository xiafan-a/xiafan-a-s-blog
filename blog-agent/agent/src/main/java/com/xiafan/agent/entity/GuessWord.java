package com.xiafan.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiafan.agent.repository.PostgresJsonTypeHandler;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** guess_words */
@TableName(value = "guess_words", autoResultMap = true)
@Data
public class GuessWord {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField("word")
    private String word;
    @TableField("hint")
    private String hint;
    @TableField("difficulty")
    private Integer difficulty = 1;

    @TableField("is_passed")
    @Getter(onMethod_ = @JsonProperty("is_passed"))
    private boolean passed = false;

    @TableField("pass_count")
    private Integer passCount = 0;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField(value = "embedding", typeHandler = PostgresJsonTypeHandler.class)
    private List<Double> embedding;
}
