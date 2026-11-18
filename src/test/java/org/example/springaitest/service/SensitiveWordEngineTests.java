package org.example.springaitest.service;

import org.example.springaitest.service.SensitiveWordEngine.Action;
import org.example.springaitest.service.SensitiveWordEngine.Hit;
import org.example.springaitest.service.SensitiveWordEngine.Rule;
import org.example.springaitest.service.SensitiveWordEngine.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感词引擎的测试。这个类是纯内存 DFA，不依赖 Spring 容器，可以直接 new。
 *
 * <p>重点验证三件容易出错的事：
 * <ol>
 *   <li>最长匹配：命中了长词就不能再报它的短前缀</li>
 *   <li>不重叠：同一个位置不能重复计数</li>
 *   <li>归一化：加了空格/零宽字符/全角的变形词仍能命中，且下标能映射回原串</li>
 * </ol>
 */
class SensitiveWordEngineTests {

    private SensitiveWordEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SensitiveWordEngine();
        engine.rebuild(List.of(
                new Rule("赌", "违法", Action.BLOCK),
                new Rule("赌博", "违法", Action.BLOCK),
                new Rule("赌博网站", "违法", Action.BLOCK),
                new Rule("敏感", "广告", Action.MASK),
                new Rule("广告", "广告", Action.REVIEW),
                new Rule("aa", "测试", Action.REVIEW)));
    }

    // ==================== 基础 ====================

    @Test
    @DisplayName("无规则：任何文本都不命中，原文原样返回")
    void noRulesHitsNothing() {
        SensitiveWordEngine empty = new SensitiveWordEngine();
        empty.rebuild(List.of());

        ScanResult result = empty.scan("赌博网站");

        assertTrue(result.hits().isEmpty());
        assertFalse(result.blocked());
        assertEquals("赌博网站", result.masked());
    }

    @Test
    @DisplayName("正常文本：不误报")
    void cleanTextNotFlagged() {
        ScanResult result = engine.scan("今天天气不错，适合出门散步。");

        assertTrue(result.hits().isEmpty());
        assertFalse(result.blocked());
        assertEquals("今天天气不错，适合出门散步。", result.masked());
    }

    // ==================== 最长匹配 / 不重叠 ====================

    @Test
    @DisplayName("最长匹配：命中「赌博网站」后不再报「赌」「赌博」")
    void longestMatchWins() {
        ScanResult result = engine.scan("赌博网站");

        assertEquals(1, result.hits().size(), "应该只报一条，实际：" + result.hits());
        assertEquals("赌博网站", result.hits().get(0).word());
    }

    @Test
    @DisplayName("不重叠：aaaa 对规则 aa 只应命中 2 次，不是 3 次")
    void hitsDoNotOverlap() {
        ScanResult result = engine.scan("aaaa");

        assertEquals(2, result.hits().size(), "实际：" + result.hits());
        assertEquals(0, result.hits().get(0).start());
        assertEquals(2, result.hits().get(1).start());
    }

    @Test
    @DisplayName("多次出现：每个位置各报一次")
    void multipleOccurrences() {
        ScanResult result = engine.scan("赌博和赌博网站");

        assertEquals(2, result.hits().size());
        assertEquals("赌博", result.hits().get(0).word());
        assertEquals("赌博网站", result.hits().get(1).word());
    }

    // ==================== 归一化绕过 ====================

    @Test
    @DisplayName("绕过·空格：中间插入空格仍能命中")
    void defeatsSpaceBypass() {
        ScanResult result = engine.scan("赌 博");
        assertEquals(1, result.hits().size(), "空格绕过没防住");
        assertEquals("赌博", result.hits().get(0).word());
    }

    @Test
    @DisplayName("绕过·零宽字符：插入 \\u200B 仍能命中")
    void defeatsZeroWidthBypass() {
        ScanResult result = engine.scan("赌\u200B博");
        assertEquals(1, result.hits().size(), "零宽字符绕过没防住");
    }

    @Test
    @DisplayName("绕过·全角 + 大小写：全角「ＡＡ」等价于「aa」")
    void defeatsFullWidthAndCaseBypass() {
        ScanResult result = engine.scan("Ａ Ａ");
        assertEquals(1, result.hits().size(), "全角/大小写绕过没防住");
    }

    @Test
    @DisplayName("归一化后下标要映射回原串：打码位置不能错位")
    void hitIndexMapsBackToOriginal() {
        String text = "今天 赌 博 了";
        ScanResult result = engine.scan(text);

        assertEquals(1, result.hits().size());
        Hit hit = result.hits().get(0);
        // 归一化时丢掉了空格，但下标必须指回原串（"赌" 在原串的 index 3）
        assertEquals(text.charAt(hit.start()), '赌');
        assertEquals('博', text.charAt(hit.end() - 1));
        assertFalse(result.masked().contains("赌"));
        assertFalse(result.masked().contains("博"));
    }

    // ==================== 分级 ====================

    @Test
    @DisplayName("BLOCK 级：blocked 为 true")
    void blockLevelSetsBlocked() {
        assertTrue(engine.scan("赌博").blocked());
    }

    @Test
    @DisplayName("MASK 级：blocked 为 false，但内容被打码")
    void maskLevelMasksButNotBlocks() {
        ScanResult result = engine.scan("这里有敏感内容");

        assertFalse(result.blocked(), "MASK 不应导致整块丢弃");
        assertEquals(1, result.hits().size());
        assertTrue(result.masked().contains("***"));
        assertFalse(result.masked().contains("敏感"));
        assertTrue(result.masked().contains("这里有"), "打码不应吃掉周围正文");
    }

    @Test
    @DisplayName("REVIEW 级：blocked 为 false，只记录不拦截")
    void reviewLevelOnlyRecords() {
        ScanResult result = engine.scan("这是一条广告");

        assertFalse(result.blocked());
        assertEquals(1, result.hits().size());
        assertEquals(Action.REVIEW, result.hits().get(0).action());
    }

    @Test
    @DisplayName("混合命中：只要有一处 BLOCK，整块就该被判定为拦截")
    void anyBlockHitsBlocksWholeChunk() {
        ScanResult result = engine.scan("这里有敏感内容和赌博网站");

        assertTrue(result.blocked());
        assertEquals(2, result.hits().size());
    }
}