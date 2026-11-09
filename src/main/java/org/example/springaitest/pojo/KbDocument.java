package org.example.springaitest.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("kb_document")
public class KbDocument {
    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("file_name")
    private String fileName;
    @TableField("char_count")
    private Integer charCount;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("status")
    private String status;

}
