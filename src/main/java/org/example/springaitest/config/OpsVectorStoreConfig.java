package org.example.springaitest.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.example.springaitest.mapper.KbChunkMapper;
import org.example.springaitest.pojo.KbChunk;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class OpsVectorStoreConfig {

    @Bean
    public SimpleVectorStore opsVectorStore(EmbeddingModel embeddingModel, KbChunkMapper chunkMapper) {

        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        // 从「块表」重建，而不是文档表 —— 向量库的单位是 chunk
        List<KbChunk> chunks = chunkMapper.selectList(
                Wrappers.<KbChunk>lambdaQuery().isNotNull(KbChunk::getVectorId));

        if (chunks.isEmpty()) {
            log.info("向量库重建：没有可加载的块");
            return store;
        }

        List<Document> documents = chunks.stream()
                .map(c -> new Document(
                        c.getVectorId(),                       // ★ 用库里的 vector_id，不是新 UUID
                        c.getContent(),                        // ★ 用块的正文
                        Map.of(
                                "userId", String.valueOf(c.getUserId()),
                                "documentId", String.valueOf(c.getDocumentId()),
                                "chunkIndex", c.getChunkIndex())))
                .toList();

        store.add(documents);                                  // 启动时会调 embedding
        log.info("向量库重建：加载 {} 个块", documents.size());
        return store;
    }
}