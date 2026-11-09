package org.example.springaitest.dto;
import lombok.Data;


// 搜索结果中的某一个 hit
public record KbSearchHit(
        int index,           // 在搜索结果中的位置
        String content,      // 这一块的正文
        double score,        // 相似度分数
        Long documentId,     // 哪篇文档
        String fileName,     // 文件名（补查来的，可以为 null）
        int chunkIndex       // 第几块
) {}