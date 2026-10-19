package org.example.springaitest.controller;

import lombok.RequiredArgsConstructor;
import org.example.springaitest.dto.SoupRequest;
import org.example.springaitest.service.SoupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/soup")
@RequiredArgsConstructor
public class SoupGameController {

    private final SoupService soupService;

    /** 新开一局 */
    @PostMapping("/start")
    public Map<String, String> start(@RequestBody SoupRequest req) {
        return soupService.start(req.getGameId());
    }

    /** 提问（是/否） */
    @PostMapping("/ask")
    public String ask(@RequestBody SoupRequest req) {
        return soupService.ask(req.getGameId(), req.getContent());
    }

    /** 提交还原故事 */
    @PostMapping("/guess")
    public String guess(@RequestBody SoupRequest req) {
        return soupService.guess(req.getGameId(), req.getContent());
    }

    /** 揭示汤底 */
    @GetMapping("/reveal")
    public String reveal(@RequestParam String gameId) {
        return soupService.reveal(gameId);
    }

    /** 历史会话列表 */
    @GetMapping("/sessions")
    public List<Map<String, String>> sessions() {
        return soupService.listSessions();
    }

    /** 某局详情 */
    @GetMapping("/{gameId}")
    public Map<String, Object> detail(@PathVariable String gameId) {
        return soupService.detail(gameId);
    }

    /** 删除一局 */
    @DeleteMapping("/{gameId}")
    public void delete(@PathVariable String gameId) {
        soupService.delete(gameId);
    }
}
