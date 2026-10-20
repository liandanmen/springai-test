package org.example.springaitest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.springaitest.dto.SoupRequest;
import org.example.springaitest.result.Result;
import org.example.springaitest.service.SoupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/soup")
@RequiredArgsConstructor
@Tag(name = "汤底游戏接口")
public class SoupGameController {

    private final SoupService soupService;
    /**
     * 新开一局
     */

    @Operation(summary = "新开一局", description = "新开一局")
    @PostMapping("/start")
    public Result<Map<String, String>> start(@RequestBody SoupRequest req) {

        Map<String, String> start = soupService.start(req.getGameId());
        return Result.success(start);
    }

    /** 提问（是/否） */
    @PostMapping("/ask")
    public Result<String> ask(@RequestBody SoupRequest req) {

        return Result.success(soupService.ask(req.getGameId(), req.getContent()));
    }

    /** 提交还原故事 */
    @PostMapping("/guess")
    @Operation(summary = "提交还原故事", description = "提交还原故事")
    public Result<String> guess(@RequestBody SoupRequest req) {
        return Result.success(soupService.guess(req.getGameId(), req.getContent()));
    }

    /** 揭示汤底 */
    @GetMapping("/reveal")
    @Operation(summary = "揭示汤底", description = "揭示汤底")
    public Result<String> reveal(@RequestParam String gameId) {
        return Result.success(soupService.reveal(gameId));
    }

    /** 历史会话列表 */
    @GetMapping("/sessions")
    @Operation(summary = "历史会话列表", description = "历史会话列表")
    public Result<List<Map<String, String>>> sessions() {
        return Result.success(soupService.listSessions());
    }

    /** 某局详情 */
    @GetMapping("/{gameId}")
    @Operation(summary = "某局详情", description = "某局详情")
    public Result<Map<String, Object>> detail(@PathVariable String gameId) {
        return Result.success(soupService.detail(gameId));
    }

    /** 删除一局 */
    @DeleteMapping("/{gameId}")
    @Operation(summary = "删除一局", description = "删除一局")
    public Result<Void> delete(@PathVariable String gameId) {
        soupService.delete(gameId);
        return Result.success();
    }
}
