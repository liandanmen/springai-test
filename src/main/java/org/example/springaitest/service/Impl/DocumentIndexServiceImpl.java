package org.example.springaitest.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.example.springaitest.constant.KbPrompt;
import org.example.springaitest.constant.MessageConstant;
import org.example.springaitest.dto.*;
import org.example.springaitest.mapper.KbChunkMapper;
import org.example.springaitest.mapper.KbDocumentMapper;
import org.example.springaitest.pojo.KbChunk;
import org.example.springaitest.pojo.KbDocument;
import org.example.springaitest.service.DocumentIndexService;
import org.example.springaitest.service.SensitiveWordEngine;
import org.example.springaitest.service.SensitiveWordService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.example.springaitest.constant.MessageConstant.EMPTY_ANSWER;
import static org.example.springaitest.constant.MessageConstant.SIMILARITY_THRESHOLD;

@Service
@RequiredArgsConstructor
public class DocumentIndexServiceImpl implements DocumentIndexService {
    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkMapper kbChunkMapper;
    private final SensitiveWordService sensitiveWordService;
    private final SimpleVectorStore vectorStore;
    @Qualifier("kbAnswerClient")
    private final ChatClient kbAnswerClient;

    /*
     * 文档索引服务
     * 负责将文档索引到向量数据库
     * */
    public KbIndexResult index(Long userId, String fileName, String rawContent) {
        //敏感词检查(复用引擎),block抛异常,mask打码后继续
        SensitiveWordEngine.ScanResult scanResult = sensitiveWordService.scan(rawContent);
        if (scanResult.blocked()) throw new RuntimeException("敏感词检查失败");
        rawContent = scanResult.masked();
        //分块
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(300)             // 每块最多 300 个 token
                .withMinChunkSizeChars(100)     // 太短的块尝试与后一块合并
                .withMinChunkLengthToEmbed(5)   // 少于 5 个字符的块直接丢弃
                .withKeepSeparator(true)        // 保留换行等分隔符
                .withPunctuationMarks(List.of('。', '！', '？', '；', '：', '\n'))    // 指定分隔符
                .build();
        //清洗内容
        String cleanedContent = cleanText(rawContent);
        List<Document> chunks = new ArrayList<>(splitter.split(new Document(cleanedContent)));
        if (chunks.size() > 1) {
            Document last = chunks.get(chunks.size() - 1);
            // 尾块太短 → 并到前一块，而不是丢弃
            if (last.getText().trim().length() < 50) {
                Document prev = chunks.get(chunks.size() - 2);
                Document merged = new Document(
                        prev.getId(),
                        prev.getText() + last.getText(),      // 拼起来
                        prev.getMetadata());
                chunks.set(chunks.size() - 2, merged);
                chunks.remove(chunks.size() - 1);
            }

        }

        /*4. insert kb_document(user_id, file_name, char_count, chunk_count)
        拿到 documentId*/

        KbDocument document = new KbDocument();
        document.setUserId(userId);
        document.setFileName(fileName);
        document.setCharCount(cleanedContent.length());
        document.setStatus("INDEX");
        document.setChunkCount(chunks.size());
        kbDocumentMapper.insert(document);
        Long documentId = document.getId();

        // 5 + 6. 一块一块入库，同时攒向量 Document
        List<Document> vectorDocs = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);

            KbChunk kbChunk = new KbChunk();
            kbChunk.setDocumentId(documentId);
            kbChunk.setUserId(userId);
            kbChunk.setChunkIndex(i);                     // ★ 用 i，不用 indexOf
            kbChunk.setContent(chunk.getText());
            kbChunkMapper.insert(kbChunk);                // 自增主键自动回填到 kbChunk.id

            String vectorId = String.valueOf(kbChunk.getId());   // ★ 数据库 id 当向量 id
            kbChunk.setVectorId(vectorId);
            kbChunkMapper.updateById(kbChunk);

            vectorDocs.add(new Document(
                    vectorId,
                    chunk.getText(),
                    Map.of(
                            "userId", String.valueOf(userId),        // ★ 统一存字符串
                            "documentId", String.valueOf(documentId),
                            "fileName", fileName,
                            "chunkIndex", i)));                      // ★ 用 i
        }
        // 6. 批量加向量（一次调用）
        if (!vectorDocs.isEmpty()) {
            vectorStore.add(vectorDocs);
        }

// 7. 标记完成
        document.setStatus("INDEXED");
        kbDocumentMapper.updateById(document);

        return new KbIndexResult(documentId, chunks.size(), cleanedContent.length());
    }

    /*
     * 文档搜索服务
     * 负责从向量数据库中搜索相似的文档
     * */
    public List<KbSearchHit> search(Long userId, String question) {

        // 1. 带过滤条件的检索（只查自己的）
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .similarityThreshold(SIMILARITY_THRESHOLD)   //相关向量阈值，测试后得出
                        .filterExpression("userId == '" + userId + "'") // ← 租户隔离
                        .build());

        // 2. 转成自己的返回对象
        //    : 用 doc.getText() / getScore() / getMetadata() 组装
        //    : （可选）收集所有 documentId，一次性查 kb_document 做 id → fileName 映射
        //    : 按 score 从高到低排序（库一般已排好，保险起见再排一次）
        // 1. 基础映射：从 Document 提取字段（此时还没有 fileName）
        List<KbSearchHit> base = docs.stream()
                .map(doc -> {
                    Map<String, Object> md = doc.getMetadata() == null ? Map.of() : doc.getMetadata();
                    // 非空判断
                    Object v = md.get("documentId"); Long docId = v == null ? null : Long.valueOf(v.toString());
//                    Long docId = Long.valueOf(String.valueOf(md.get("documentId")));
                    Integer chunkIndex = Integer.valueOf(String.valueOf(md.get("chunkIndex")));
                    double score = doc.getScore() == null ? 0.0 : doc.getScore();
                    return new KbSearchHit(
                            docs.indexOf(doc) + 1,  // 索引从 1 开始
                            doc.getText(),
                            score,
                            docId,
                            null,                                  // fileName 稍后补
                            chunkIndex == null ? 0 : chunkIndex);   // 防御 null
                })
                .filter(hit -> hit.documentId() != null)            // 过滤掉无 documentId 的
                .toList();

// 2. 收集 documentId，一次性查 kb_document 做 id -> fileName 映射
        Set<Long> docIds = base.stream()
                .map(KbSearchHit::documentId)
                .collect(Collectors.toSet());
        Map<Long, String> idToFileName = docIds.isEmpty()
                ? Map.of()
                : kbDocumentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(KbDocument::getId, KbDocument::getFileName));

// 3. 回填 fileName + 按 score 降序（保险起见再排一次）
        List<KbSearchHit> filteredDocs = base.stream()
                .map(hit -> new KbSearchHit(
                        hit.index(),
                        hit.content(),
                        hit.score(),
                        hit.documentId(),
                        idToFileName.get(hit.documentId()),   // 可为 null
                        hit.chunkIndex()))
                .sorted(Comparator.comparingDouble(KbSearchHit::score).reversed())
                .toList();
        return filteredDocs;

    }


    /*
     * 问题回答服务
     * 负责根据用户问题从向量数据库中搜索相似的文档并返回答案
     * */
    @Override
    public KbAskResponse ask(Long userId, String trim) {
        //首先检索
        List<KbSearchHit> searchHits = search(userId, trim);
        if (searchHits.isEmpty()) {   //未检索到直接返回不调模型
            return new KbAskResponse(false, EMPTY_ANSWER, null);
        }
        //拼资料，编号与 refs 下标严格对应（从 1 开始）
        StringBuilder ctx = new StringBuilder();
        for (KbSearchHit h : searchHits) {
            ctx.append("[").append(h.index()).append("] 来自 ")
                    .append(h.fileName()).append(" 第").append(h.chunkIndex() + 1).append("段\n")
                    .append(h.content()).append("\n\n");
        }
        //调模型
        String answer = kbAnswerClient.prompt()
                .system(KbPrompt.KB_SYSTEM)          //文档问答规则
                .user("资料：\n" + ctx + "\n用户问题：" + trim)
                .call()
                .content();
// 模型引用了资料 → 说明它找到了依据
        boolean cited = answer != null && answer.matches("(?s).*\\[\\d+\\].*");
        return new KbAskResponse(cited, answer, cited ? searchHits : List.of());
//        return new KbAskResponse(true, answer, searchHits);
    }

    @Override
    public Flux<String> askStream(String question, List<KbSearchHit> hits) {
        return kbAnswerClient.prompt()
                .system(KbPrompt.KB_SYSTEM)
                .user(buildUserMessage(question, hits))
                .stream()
                .content();
    }

    //列表
    public List<KbDocumentVO> list(Long userId) {
        return kbDocumentMapper.selectList(
                        Wrappers.<KbDocument>lambdaQuery()
                                .eq(KbDocument::getUserId, userId)
                                .orderByDesc(KbDocument::getId))
                .stream()
                .map(d -> new KbDocumentVO(
                        d.getId(),
                        d.getFileName(),
                        d.getChunkCount(),
                        d.getCharCount(),
                        d.getStatus(),
                        d.getCreateTime()))
                .toList();
    }

    //文档删除
    public String delete(Long userId,Long documentId){
        //首先校验
        KbDocument kbDocument = kbDocumentMapper.selectOne(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getUserId, userId)
                .eq(KbDocument::getId, documentId));
        if (kbDocument==null){
            throw new RuntimeException("文档不存在");
        }
        //找出所有向量，首先删除向量，再删数据库
        List<KbChunk> kbChunks = kbChunkMapper.selectList(Wrappers.<KbChunk>lambdaQuery()
                .eq(KbChunk::getDocumentId, documentId));
        List<String> vectorIds = kbChunks.stream().map(KbChunk::getVectorId).filter(Objects::nonNull).toList();

        if (!vectorIds.isEmpty()){
            vectorStore.delete(vectorIds);
        }
        kbChunkMapper.delete(Wrappers.<KbChunk>lambdaQuery().eq(KbChunk::getDocumentId, documentId));
        kbDocumentMapper.deleteById(documentId);
        return MessageConstant.DELETE_SUCCESS;
    }

    private String buildUserMessage(String question, List<KbSearchHit> hits) {
        //拼资料，编号与 refs 下标严格对应（从 1 开始）
        StringBuilder ctx = new StringBuilder();
        for (KbSearchHit h : hits) {
            ctx.append("[").append(h.index()).append("] 来自 ")
                    .append(h.fileName()).append(" 第").append(h.chunkIndex() + 1).append("段\n")
                    .append(h.content()).append("\n\n");
        }
        return "资料：\n" + ctx + "\n用户问题：" + question;
    }


    static String cleanText(String raw) {
        return raw.replace("\r\n", "\n")
                // 行内单换行（前后都不是空行）→ 是排版折行，删掉
                .replaceAll("(?<=\\S)\\n(?=\\S)", "")
                // 三个以上连续换行 → 压成一个空行（段落分隔）
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
