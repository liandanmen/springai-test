package org.example.springaitest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.springaitest.dto.SensitiveRequest;
import org.example.springaitest.result.Result;
import org.example.springaitest.service.SensitiveWordEngine;
import org.example.springaitest.service.SensitiveWordService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sensitive")
@Tag(name = "敏感词接口")
public class SensitiveWordController {
    private final SensitiveWordService sensitiveWordService;
    @PostMapping("/check")
    @Operation(summary = "检查敏感词", description = "检查敏感词，文本不能为空")
    public Result<SensitiveWordEngine.ScanResult> check(@RequestBody SensitiveRequest request) {

        if(request.text() == null || request.text().isBlank()){
            return Result.error("文本不能为空");
        }
        SensitiveWordEngine.ScanResult scan = sensitiveWordService.scan(request.text());
        return Result.success(scan);

    }

}
