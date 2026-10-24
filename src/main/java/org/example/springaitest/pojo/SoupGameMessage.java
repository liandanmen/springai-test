package org.example.springaitest.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("soup_game_message")
public class SoupGameMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("game_id")
    private String gameId;
    private String type;
    private String content;
    @TableField("is_guess")
    private Boolean isGuess;
}
