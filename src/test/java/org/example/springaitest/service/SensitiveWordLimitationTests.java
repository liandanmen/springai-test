package org.example.springaitest.service;

import org.example.springaitest.service.SensitiveWordEngine.Action;
import org.example.springaitest.service.SensitiveWordEngine.Rule;
import org.example.springaitest.service.SensitiveWordEngine.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 已知局限测试（characterization test）。
 *
 * <p>这个文件的测试不是在"验证功能正确"，而是在**记录当前实现防不住什么**。
 * 为什么要记录：面试被问到"你的敏感词方案有什么局限"时，
 * 能打开这个文件说"我写了测试把这个缺陷钉住了，避免以后有人误以为它能防住"，
 * 比嘴上说"应该防不住吧"强得多。
 *
 * <p>写法要点：断言的是"现在确实识别不出来"，所以测试会通过（绿）。
 * 等哪天真的实现了拼音/繁体识别，这两条测试就会变红——那时再改成断言"能识别"。
 */
class SensitiveWordLimitationTests {

    private SensitiveWordEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SensitiveWordEngine();
        engine.rebuild(List.of(
                new Rule("赌博", "违法", Action.BLOCK)));
    }

    // =====================================================================
    // 任务一：把"拼音绕过"这个缺陷固化成一条测试
    // =====================================================================

    @Test
    void pinyinBypassIsNotDetected() {
        // 现状：引擎只做字符级归一化（空格/零宽/全角/大小写），完全不懂拼音。
        //
        // 你要写的步骤：
        //   1. 调 engine.scan("du bo")，拿到 ScanResult
        //   2. 断言：result.hits() 是空的        ← 这就是"防不住"的证明
        //   3. 断言：result.blocked() 是 false   ← 所以整块不会被丢弃
        //
        // 提示：用 assertTrue(result.hits().isEmpty())
        //
        // 写完后跑：mvn test -Dtest=SensitiveWordLimitationTests
        // 应该通过（绿）。如果红了，说明你的断言写反了。
        ScanResult result = engine.scan("du bo");
        assertTrue(result.hits().isEmpty());
        assertFalse(result.blocked());

    }

    // =====================================================================
    // 任务二：把"繁体字绕过"这个缺陷固化成一条测试
    // =====================================================================
    @Test
    void traditionalChineseBypassIsNotDetected() {
        // 现状：归一化里只做了全角转半角和大小写，没有 繁体→简体 的转换。
        //       规则里是"赌博"（简体），用户输入"賭博"（繁体）就绕过了。
        //
        // 你要写的步骤：
        //   1. 调 engine.scan("賭博")            ← 注意这两个字是繁体
        //   2. 断言：result.hits() 是空的
        //
        // 写完后跑：mvn test -Dtest=SensitiveWordLimitationTests
        ScanResult result = engine.scan("賭博");
        assertTrue(result.hits().isEmpty());
        assertFalse(result.blocked());

    }
}