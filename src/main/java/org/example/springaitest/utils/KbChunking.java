package org.example.springaitest.utils;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档清洗与分块。
 *
 * <p>RAG 链路里唯一"纯函数"的一环（输入字符串、输出字符串列表，不碰数据库、不调模型），
 * 所以单独抽出来：既能被单元测试覆盖，也能让评测脚本换参数跑不同配置。
 *
 * <p>注意：分块粒度直接决定检索质量——块太大，召回的内容里混着无关段落；
 * 块太小，一句话被切断，向量表达不完整。
 */
public final class KbChunking {

    /** 每块最多多少个 token */
    public static final int CHUNK_SIZE = 300;
    /** 小于该字符数的块会尝试与后一块合并（避免碎块） */
    public static final int MIN_CHUNK_SIZE_CHARS = 100;
    /** 少于该字符数的块直接丢弃（如单独的"附"字） */
    public static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;
    /** 尾块小于该字符数时并进前一块，不单独成为一个向量 */
    public static final int TAIL_MERGE_THRESHOLD = 50;
    /** 优先在这些标点处下刀，避免把一个句子从中间截断 */
    public static final List<Character> PUNCTUATION_MARKS = List.of('。', '！', '？', '；', '：', '\n');

    private KbChunking() {
    }

    /**
     * 清洗：把 PDF/Word 里"排版用的折行"去掉，同时保留真正的段落分隔。
     *
     * <ol>
     *   <li>统一换行符为 \n</li>
     *   <li>行内单换行（前后都不是空行）→ 是排版折行，删掉</li>
     *   <li>三个以上连续换行 → 压成一个空行（段落分隔）</li>
     * </ol>
     */
    public static String cleanText(String raw) {
        if (raw == null) return "";
        return raw.replace("\r\n", "\n")
                // 行内单换行（前后都不是空行）→ 是排版折行，删掉
                .replaceAll("(?<=\\S)\\n(?=\\S)", "")
                // 三个以上连续换行 → 压成一个空行（段落分隔）
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public static List<Document> splitToDocuments(String cleanedText) {
        return splitToDocuments(cleanedText, CHUNK_SIZE);
    }

    /**
     * 分块，返回 Spring AI 的 Document（保留 id / metadata 语义，方便直接喂给向量库）。
     *
     * @param chunkSize 每块 token 上限；独立成参是为了让评测能跑不同配置
     */
    public static List<Document> splitToDocuments(String cleanedText, int chunkSize) {
        if (cleanedText == null || cleanedText.isBlank()) {
            return List.of();
        }
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
                .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
                .withKeepSeparator(true)
                .withPunctuationMarks(PUNCTUATION_MARKS)
                .build();

        List<Document> chunks = new ArrayList<>(splitter.split(new Document(cleanedText)));
        mergeShortTail(chunks);
        return chunks;
    }

    /** 只要文本，不要 Document —— 评测和测试用起来更轻 */
    public static List<String> split(String cleanedText) {
        return split(cleanedText, CHUNK_SIZE);
    }

    public static List<String> split(String cleanedText, int chunkSize) {
        return splitToDocuments(cleanedText, chunkSize).stream()
                .map(Document::getText)
                .toList();
    }

    /**
     * 尾块太短就并进前一块。
     *
     * <p>否则最后一句话会单独变成一个向量，召回它时上下文太单薄，
     * 而且它在库里占一个位置、白花一次 embedding 的钱。
     */
    static void mergeShortTail(List<Document> chunks) {
        if (chunks.size() <= 1) return;

        Document last = chunks.get(chunks.size() - 1);
        if (last.getText() == null || last.getText().trim().length() >= TAIL_MERGE_THRESHOLD) {
            return;
        }
        Document prev = chunks.get(chunks.size() - 2);
        Document merged = new Document(prev.getId(), prev.getText() + last.getText(), prev.getMetadata());
        chunks.set(chunks.size() - 2, merged);
        chunks.remove(chunks.size() - 1);
    }
}