package org.example.springaitest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.springaitest.constant.MessageConstant;
import org.example.springaitest.dto.*;
import org.example.springaitest.exception.BaseException;
import org.example.springaitest.handler.JwtAuthenticationInterceptor;
import org.example.springaitest.result.Result;
import org.example.springaitest.service.Impl.DocumentIndexServiceImpl;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
@Tag(name = "知识库")
public class KbController {

    private final DocumentIndexServiceImpl documentIndexService;
    private final ObjectMapper objectMapper;

    @PostMapping("/index")
    @Operation(summary = "知识库索引")
    public Result<KbIndexResult> index(@RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId,
                                       @RequestBody KbIndexRequest req
    ) {
        if (req == null || req.fileName() == null || req.fileName().isBlank() || req.content() == null || req.content().isBlank()) {
            throw new BaseException("文件名或内容不能为空");
        }
        KbIndexResult re = documentIndexService.index(userId, req.fileName(), req.content());
        return Result.success(re);
    }

    @PostMapping("/search")
    public Result<List<KbSearchHit>> search(
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestBody KbSearchRequest req) {
        // 参数校验 → 调 service → Result.success(list)
        if (req == null || req.question() == null || req.question().isBlank()) {
            throw new BaseException("问题不能为空");
        }
        return Result.success(documentIndexService.search(userId, req.question().trim()));
    }

    @PostMapping("/ask")
    @Operation(summary = "知识库问答", description = "基于文档的 RAG 问答")
    public Result<KbAskResponse> ask(
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestBody KbAskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new BaseException("问题不能为空");
        }
        String question = request.question().trim();

        return Result.success((KbAskResponse) documentIndexService.ask(userId, request.question().trim()));
    }

    //知识库流式问答
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "知识库流式问答", description = "基于文档的 RAG 流式问答")
    public Flux<ServerSentEvent<String>> stream(
            @RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId,
            @RequestBody KbAskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new BaseException("问题不能为空");
        }

        String question = request.question().trim();
        //首先检索(同步，等待200ms)
        List<KbSearchHit> searchHits = documentIndexService.search(userId, question);
        /*ServerSentEvent<String> refsEvent= ServerSentEvent.builder(
                        String.valueOf(objectMapper.writeValueIntoBuffer(searchHits)))
                .event("search").build();*/
        //构建搜索结果的 ServerSentEvent
        ServerSentEvent<String> refsEvent = ServerSentEvent.builder(
                        objectMapper.writeValueAsString(searchHits))
                .event("refs").build();
        //构建完成事件的 ServerSentEvent
        ServerSentEvent<String> doneEvent = ServerSentEvent.builder(
                "").event("done").build();

        if (searchHits.isEmpty()) {   //未检索到直接返回不调模型
            return Flux.just(refsEvent,
                    ServerSentEvent.builder(MessageConstant.EMPTY_ANSWER).event("message").build(),
                    doneEvent);
        }

        //有依据，调模型
        Flux<ServerSentEvent<String>> flux = documentIndexService.askStream(question, searchHits)
                .map(s -> ServerSentEvent.builder(s).event("message").build())
                //错误处理
                .onErrorResume(ex -> Flux.just(ServerSentEvent.builder("生成中断" + ex.getMessage()).event("error").build()));
        return Flux.concat(Flux.just(refsEvent), flux, Flux.just(doneEvent));
    }

    //文档列表
    @GetMapping("/documents")
    @Operation(summary = "文档列表")
    public Result<List<KbDocumentVO>> list(@RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId) {
        return Result.success(documentIndexService.list(userId));
    }

    //删除文档接口
    @DeleteMapping("/document/{id}")
    @Operation(summary = "文档删除",description = "知识库文档列表删除")
    public Result<String> delete(@RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE)Long userId,
                         @PathVariable("id") Long id){
        String delete = documentIndexService.delete(userId, id);
        return Result.success(delete);
    }

    //文档上传接口
    @PostMapping("/upload")
    @Operation(summary = "文档上传", description = "上传文档到知识库")
    public Result<KbIndexResult> upload(@RequestAttribute(JwtAuthenticationInterceptor.USER_ID_ATTRIBUTE) Long userId,
                                @RequestParam("file") MultipartFile file) throws IOException {

        String original = Paths.get(file.getOriginalFilename()).getFileName().toString();
        String lower = original == null ? "" : original.toLowerCase();
        //加上后缀白名单，确保格式
        if(!lower.endsWith(".txt")&&!lower.endsWith(".md")){
            throw new BaseException("文件格式错误，只支持 txt 和 md 格式");
        }
        //限定文件大小
        if(file.getSize() > 1024 * 1024 * 2) {
            throw new BaseException("文件大小不能超过 2MB");
        }
        //编码格式检测
        byte[] bytes = file.getBytes();
        String content = readText(bytes);
        if(content == null || content.isEmpty()) {
            throw new BaseException("文件内容不能为空");
        }
        if (content.indexOf('\uFFFD') >= 0) {
            throw new BaseException("该文件不是纯文本,无法解析");
        }
        KbIndexResult result = documentIndexService.index(userId, original, content);
        return Result.success(result);
    }

    // 读取文本内容，优先 UTF-8，其次 GBK
    private String readText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        // UTF-8 解码失败的典型标志：出现了替换字符 U+FFFD
        if (utf8.indexOf('\uFFFD') >= 0) {
            return new String(bytes, Charset.forName("GBK"));   // 退回 GBK
        }
        return utf8;
    }
}
