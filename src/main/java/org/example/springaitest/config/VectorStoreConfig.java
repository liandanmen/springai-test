package org.example.springaitest.config;

import org.example.springaitest.constant.SoupPrompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore soupVectorStore(EmbeddingModel embeddingModel) {
        // 1. 创建内存向量库
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        // 2. 把你准备好的海龟汤文档加进去,向量库应存汤面，汤底交给主持人prompt防止剧透
        // 实际项目中这里应该是从文件或数据库读取
        List<Document> docs = new java.util.ArrayList<>();
        for (int i = 0; i < SoupPrompt.SOUP_PRESETS.size(); i++) {
            String[] soup = SoupPrompt.SOUP_PRESETS.get(i);
            docs.add(new Document(soup[0], Map.of("type", "soup", "index", i)));
        }
        vectorStore.add(docs);
        return vectorStore;
    }
    }
