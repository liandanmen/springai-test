package org.example.springaitest.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.springaitest.mapper.SensitiveRuleMapper;
import org.example.springaitest.pojo.SensitiveRule;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensitiveWordService {
    private final SensitiveRuleMapper mapper;

    private final SensitiveWordEngine engine;

    @PostConstruct
    public void reload() {
        // 1. 查 enable = true 的全部规则（不带任何 word 条件！）
        List<SensitiveRule> s = mapper.selectList(
                Wrappers.<SensitiveRule>lambdaQuery()
                        .eq(SensitiveRule::getEnable, true)
        );
        // 2. SensitiveRule → engine.Rule 的 List
        //    （字符串 "MASK" → Action.MASK，注意非法值要兜底）
        List<SensitiveWordEngine.Rule> list = s.stream()
                .map(rule -> new SensitiveWordEngine.Rule(rule.getWord(), rule.getCategory(), SensitiveWordEngine.Action.valueOf(rule.getAction())))
                .toList();

        log.info("已加载 {} 条敏感词规则", list.size());
        // 3. engine.rebuild(list)
        // 4. log.info("已加载 {} 条敏感词规则", list.size());
        engine.rebuild(list);
    }

    /** 对外的检测入口：只查内存，绝不查库 */
    public SensitiveWordEngine.ScanResult scan(String text) {
        return engine.scan(text);
    }
}