package org.example.springaitest.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.example.springaitest.mapper.KbChunkMapper;
import org.example.springaitest.pojo.KbChunk;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 向量库装配。
 *
 * <p>{@link SimpleVectorStore} 是**内存实现**，进程一重启就没了。原来的做法是
 * 每次启动都从 `kb_chunk` 全表捞出来再 {@code add()} 一遍——而 {@code add()} 会
 * 重新调用 embedding 接口。文档一多，启动就是几百次 API 调用：慢，而且重复花钱。
 *
 * <p>现在改成：<b>索引落盘 + 启动优先加载</b>
 * <ol>
 *   <li>有索引文件且没要求强制重建 → 直接 {@code load()}，零 embedding 调用</li>
 *   <li>文件不存在或加载失败 → 从数据库重建，然后落盘</li>
 *   <li>新增/删除文档后由 service 调用落盘，保证文件与库一致</li>
 * </ol>
 *
 * <p>注意：这只是"把内存索引持久化"，不是真正的向量数据库。
 * 多实例部署仍然各存各的，要真正解决得上 Milvus / pgvector / Redis Stack。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(KbProperties.class)
public class OpsVectorStoreConfig {

    @Bean
    public SimpleVectorStore opsVectorStore(EmbeddingModel embeddingModel,
                                            KbChunkMapper chunkMapper,
                                            KbProperties props) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File indexFile = new File(props.indexFile());

        // 1. 优先从落盘文件加载：这条路径不调 embedding，启动最快
        if (!Boolean.TRUE.equals(props.rebuildOnStart()) && indexFile.exists() && indexFile.length() > 0) {
            try {
                store.load(indexFile);
                log.info("向量索引从文件加载完成 path={} 大小={}KB（未调用 embedding 接口）",
                        indexFile.getAbsolutePath(), indexFile.length() / 1024);
                return store;
            } catch (Exception e) {
                log.warn("向量索引文件损坏或不可读，回退到从数据库重建 path={}", indexFile.getAbsolutePath(), e);
            }
        }

        // 2. 从「块表」重建，而不是文档表 —— 向量库的单位是 chunk
        List<KbChunk> chunks = chunkMapper.selectList(
                Wrappers.<KbChunk>lambdaQuery().isNotNull(KbChunk::getVectorId));

        if (chunks.isEmpty()) {
            log.info("向量库重建：没有可加载的块");
            return store;
        }

        List<Document> documents = chunks.stream()
                .map(c -> new Document(
                        c.getVectorId(),                       // 用库里的 vector_id，不是新 UUID
                        c.getContent(),                        // 用块的正文
                        Map.of(
                                "userId", String.valueOf(c.getUserId()),
                                "documentId", String.valueOf(c.getDocumentId()),
                                "chunkIndex", c.getChunkIndex())))
                .toList();

        store.add(documents);                                  // 重建时必然要调一次 embedding
        log.info("向量库从数据库重建完成：加载 {} 个块，已调用 embedding 接口", documents.size());

        saveQuietly(store, indexFile);
        return store;
    }

    /** 落盘失败不致命：内存索引还在，只是下次启动要重建 */
    static void saveQuietly(SimpleVectorStore store, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("创建索引目录失败，跳过落盘 dir={}", parent.getAbsolutePath());
                return;
            }
            store.save(file);
            log.info("向量索引已落盘 path={} 大小={}KB", file.getAbsolutePath(), file.length() / 1024);
        } catch (Exception e) {
            log.warn("向量索引落盘失败，下次启动将重新 embedding", e);
        }
    }
}