package org.example.springaitest.dto;

//文档列表
public record KbDocumentVO(
        Long id,
        String fileName,
        int chunkCount,
        int charCount,
        String status,
        String createAt
) {}
