package org.example.springaitest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库检索参数。
 *
 * <p>这三个数字以前写死在代码里（`KbChunking.CHUNK_SIZE`、`search()` 里的 5 和 0.30），
 * 调一次要重新编译。外置到配置后：
 * <ul>
 *   <li>调参不用改代码，改 application.yaml 即可</li>
 *   <li>评测脚本可以稳定复现某组参数</li>
 *   <li>面试时能直接指出"这些值是可配置的，默认值来自评测"</li>
 * </ul>
 *
 * <p>默认值取自 {@code RAG_EVAL.md} 的评测结论。
 */
@ConfigurationProperties(prefix = "springai.kb")
public record KbProperties(
        /** 每块 token 上限；300 在评测中 hit@1 明显优于 500 */
        Integer chunkSize,
        /** 检索召回条数 */
        Integer topK,
        /** 相似度阈值，低于该值的块直接丢弃 */
        Double similarityThreshold,
        /** 向量索引落盘路径；SimpleVectorStore 是内存实现，靠这个文件避免重启重建 */
        String indexFile,
        /** true = 每次启动都从数据库重建索引（会重新调用 embedding） */
        Boolean rebuildOnStart
) {

    public KbProperties {
        if (chunkSize == null) chunkSize = 300;
        if (topK == null) topK = 5;
        if (similarityThreshold == null) similarityThreshold = 0.30;
        if (indexFile == null || indexFile.isBlank()) indexFile = "data/vector-index.json";
        if (rebuildOnStart == null) rebuildOnStart = false;
    }
}