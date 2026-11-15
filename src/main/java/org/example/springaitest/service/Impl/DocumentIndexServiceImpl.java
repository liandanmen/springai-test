package org.example.springaitest.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springaitest.constant.KbPrompt;
import org.example.springaitest.constant.MessageConstant;
import org.example.springaitest.exception.BaseException;
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

@Slf4j
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
        long start = System.currentTimeMillis();
        log.info("开始索引 userId={} 文件名={} 原始字符数={}",
                userId, fileName, rawContent == null ? 0 : rawContent.length());

        if (rawContent == null || rawContent.isBlank()) {
            log.warn("索引中止：内容为空 userId={} 文件名={}", userId, fileName);
            throw new BaseException("文档内容为空");
        }

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

        // 3. 块级敏感词过滤：BLOCK 整块丢弃、MASK 块内打码、REVIEW 放行并记日志
        //    为什么按块而不是按整篇：整篇里有一个 BLOCK 词就拒收，会误杀正常文档
        List<String> keptContents = new ArrayList<>();
        int dropped = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String text = chunks.get(i).getText();
            SensitiveWordEngine.ScanResult scan = sensitiveWordService.scan(text);
            List<String> words = scan.hits().stream().map(SensitiveWordEngine.Hit::word).toList();

            if (scan.blocked()) {
                dropped++;
                log.warn("第 {} 块命中 BLOCK 敏感词，整块丢弃 userId={} 文件名={} 字数={} 命中={}",
                        i, userId, fileName, text.length(), words);
                continue;
            }
            if (!words.isEmpty()) {
                log.info("第 {} 块命中敏感词，MASK 已打码 / REVIEW 放行 userId={} 文件名={} 命中={}",
                        i, userId, fileName, words);
            }
            keptContents.add(scan.masked());
        }

        /*4. insert kb_document(user_id, file_name, char_count, chunk_count)
        拿到 documentId*/

        if (keptContents.isEmpty()) {
            log.warn("索引中止：全部块均命中 BLOCK 敏感词 userId={} 文件名={} 原块数={}",
                    userId, fileName, chunks.size());
            throw new BaseException(MessageConstant.DOC_ALL_BLOCKED);
        }

        KbDocument document = new KbDocument();
        document.setUserId(userId);
        document.setFileName(fileName);
        document.setCharCount(cleanedContent.length());
        document.setStatus("INDEX");
        document.setChunkCount(keptContents.size());
        kbDocumentMapper.insert(document);
        Long documentId = document.getId();

        // 5 + 6. 一块一块入库，同时攒向量 Document
        List<Document> vectorDocs = new ArrayList<>();

        for (int i = 0; i < keptContents.size(); i++) {
            String content = keptContents.get(i);

            KbChunk kbChunk = new KbChunk();
            kbChunk.setDocumentId(documentId);
            kbChunk.setUserId(userId);
            kbChunk.setChunkIndex(i);                     // ★ 用 i，不用 indexOf
            kbChunk.setContent(content);
            kbChunkMapper.insert(kbChunk);                // 自增主键自动回填到 kbChunk.id

            String vectorId = String.valueOf(kbChunk.getId());   // ★ 数据库 id 当向量 id
            kbChunk.setVectorId(vectorId);
            kbChunkMapper.updateById(kbChunk);

            vectorDocs.add(new Document(
                    vectorId,
                    content,
                    Map.of(
                            "userId", String.valueOf(userId),        // ★ 统一存字符串
                            "documentId", String.valueOf(documentId),
                            "fileName", fileName,
                            "chunkIndex", i)));                      // ★ 用 i
        }
        // 6+7. 批量加向量（★ 只调一次！调两次 = 重复 embedding + 重复向量 + 双倍计费）
        // 假如向量接口超时，文档库已经写入但是向量库没有 → 标记 FAILED
        try {
            if (!vectorDocs.isEmpty()) {
                vectorStore.add(vectorDocs);
            }
            document.setStatus("INDEXED");
        } catch (Exception e) {
            document.setStatus("FAILED");
            log.error("向量化失败 userId={} documentId={} 块数={}", userId, documentId, vectorDocs.size(), e);
            throw e;
        } finally {
            kbDocumentMapper.updateById(document);
        }

        log.info("索引完成 userId={} 文件名={} documentId={} 字符数={} 原切分块数={} 入库块数={} 丢弃块数={} 状态={} 耗时={}ms",
                userId, fileName, documentId, cleanedContent.length(), chunks.size(),
                keptContents.size(), dropped, document.getStatus(),
                System.currentTimeMillis() - start);

        return new KbIndexResult(documentId, keptContents.size(), cleanedContent.length());
    }

    /*
     * 文档搜索服务
     * 负责从向量数据库中搜索相似的文档
     * */
    public List<KbSearchHit> search(Long userId, String question) {
        long start = System.currentTimeMillis();

        // 1. 带过滤条件的检索（只查自己的）
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .similarityThreshold(SIMILARITY_THRESHOLD)   //相关向量阈值，测试后得出
                        .filterExpression("userId == '" + userId + "'") // ← 租户隔离
                        .build());

        // 2. 先按分数降序，再顺序编号
        //    ★ index 必须和最终顺序一致：前端靠 [n] 跳引用，编号错了就跳错
        List<Document> sorted = docs.stream()
                .sorted(Comparator.comparingDouble(
                        (Document d) -> d.getScore() == null ? 0.0 : d.getScore()).reversed())
                .toList();

        List<KbSearchHit> base = new ArrayList<>();
        for (Document doc : sorted) {
            Map<String, Object> md = doc.getMetadata() == null ? Map.of() : doc.getMetadata();
            Object v = md.get("documentId");
            if (v == null) {
                // 元数据不完整 → 跳过这条，不能让整次检索抛异常
                log.warn("向量元数据缺少 documentId，已跳过 vectorId={}", doc.getId());
                continue;
            }
            Long docId = Long.valueOf(v.toString());
            Object ci = md.get("chunkIndex");
            int chunkIndex = ci == null ? 0 : Integer.parseInt(ci.toString());
            double score = doc.getScore() == null ? 0.0 : doc.getScore();
            base.add(new KbSearchHit(base.size() + 1, doc.getText(), score, docId, null, chunkIndex));
        }

        // 3. 收集 documentId，一次性查 kb_document 做 id -> fileName 映射
        Set<Long> docIds = base.stream()
                .map(KbSearchHit::documentId)
                .collect(Collectors.toSet());
        Map<Long, String> idToFileName = docIds.isEmpty()
                ? Map.of()
                : kbDocumentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(KbDocument::getId, KbDocument::getFileName));

        List<KbSearchHit> hits = base.stream()
                .map(h -> new KbSearchHit(h.index(), h.content(), h.score(), h.documentId(),
                        idToFileName.get(h.documentId()), h.chunkIndex()))
                .toList();

        double top = hits.isEmpty() ? 0.0 : hits.get(0).score();
        log.info("检索完成 userId={} 问题长度={} 召回={} 最高分={} 阈值={} 耗时={}ms",
                userId, question == null ? 0 : question.length(), hits.size(),
                String.format("%.3f", top), SIMILARITY_THRESHOLD,
                System.currentTimeMillis() - start);

        return hits;
    }


    /*
     * 问题回答服务
     * 负责根据用户问题从向量数据库中搜索相似的文档并返回答案
     * */
    @Override
    public KbAskResponse ask(Long userId, String trim) {
        long start = System.currentTimeMillis();

        //首先检索
        List<KbSearchHit> searchHits = search(userId, trim);
        if (searchHits.isEmpty()) {   //未检索到直接返回不调模型
            log.info("问答结束（未召回，未调用模型）userId={} 耗时={}ms",
                    userId, System.currentTimeMillis() - start);
            return new KbAskResponse(false, EMPTY_ANSWER, List.of());  //集合类型不能返回null，前端回NPE
        }

        //调模型（拼资料统一走 buildUserMessage，别在两处各拼一遍）
        String answer = kbAnswerClient.prompt()
                .system(KbPrompt.KB_SYSTEM)          //文档问答规则
                .user(buildUserMessage(trim, searchHits))
                .call()
                .content();

        // 模型引用了资料 → 说明它找到了依据
        boolean cited = answer != null && answer.matches("(?s).*\\[\\d+\\].*");
        log.info("问答完成 userId={} 召回={} 有引用={} 答案长度={} 耗时={}ms",
                userId, searchHits.size(), cited,
                answer == null ? 0 : answer.length(), System.currentTimeMillis() - start);
        return new KbAskResponse(cited, answer, cited ? searchHits : List.of());
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
                        d.getCreateAt()))
                .toList();
    }

    //文档删除
    public String delete(Long userId,Long documentId){
        //首先校验
        KbDocument kbDocument = kbDocumentMapper.selectOne(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getUserId, userId)
                .eq(KbDocument::getId, documentId));
        if (kbDocument==null){
            log.warn("删除失败：文档不存在或不属于该用户 userId={} documentId={}", userId, documentId);
            throw new BaseException("文档不存在");
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

        log.info("删除文档 userId={} documentId={} 文件名={} 删除块数={} 删除向量数={}",
                userId, documentId, kbDocument.getFileName(), kbChunks.size(), vectorIds.size());
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
