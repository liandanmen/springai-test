package org.example.springaitest.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("SOUP_GAME")
public class SoupGame {
    @TableId
    private String gameId;
    private Long userId;
    private String conversationId;
    private String surface;
    private String secret;
    private String status;

}
