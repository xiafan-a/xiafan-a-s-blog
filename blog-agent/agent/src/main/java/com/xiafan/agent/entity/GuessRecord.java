package com.xiafan.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

/** guess_records */
@TableName("guess_records")
@Data
public class GuessRecord {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField("guess_word_id")
    private Integer guessWordId;
    @TableField("guess")
    private String guess;
    @TableField("similarity")
    private Double similarity;

    @TableField("is_correct")
    @Getter(onMethod_ = @JsonProperty("is_correct"))
    private boolean isCorrect = false;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
