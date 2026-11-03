package org.example.springaitest.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sensitive_rule")
public class SensitiveRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;    // 敏感词
    private String category;    // 类别
    private String action;    // 动作
    private Boolean enable;    // 是否启用
    private String createdAt;    // 创建时间
    private String updatedAt;    // 更新时间
}
