package org.example.springaitest.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("kb_chunk")
public class KbChunk {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;
    @TableField("document_id")
    private Long documentId;
    @TableField("user_id")
    private Long userId;
    @TableField("chunk_index")
    private Integer chunkIndex;
    @TableField("content")
    private String content;
    @TableField("vector_id")
    private String vectorId;
    @TableField("created_at")
    private String createdAt;
}
