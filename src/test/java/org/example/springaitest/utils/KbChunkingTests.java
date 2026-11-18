package org.example.springaitest.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分块是 RAG 里最容易被"感觉"糊弄的一步——参数改一下，检索效果就变，
 * 但没人知道内容有没有被切丢。这组测试守住三件事：
 * 清洗规则正确、分块不丢内容、尾块合并生效。
 */
class KbChunkingTests {

    /** 故意写成"说明书"的样子：章节标题 + 长段落 + 指代（如上所述） */
    private static final String MANUAL = """
            第一章 产品简介

            本产品是一款家用空气炸锅，额定功率 1500 瓦，容量 5 升，适用家庭日常烹饪。产品采用热风循环技术，
            通过高速气流在腔体内形成对流，使食物表面快速脱水，从而达到近似油炸的口感。相比传统油炸，本产品
            可减少约百分之八十的用油量。产品出厂前经过高温老化测试，请放心使用。

            第二章 首次使用前的准备

            首次使用前，请先取出炸篮和内胆，用温水和中性洗涤剂清洗干净，再用干布擦干。请注意，炸篮和炸锅内胆
            均带有不粘涂层，禁止使用钢丝球等硬质清洁工具，以免刮伤涂层。清洗完成后，请将设备置于平整台面上，
            确保进风口和出风口均无遮挡物，且与墙壁保持至少十厘米的距离。

            第三章 使用方法

            将食材放入炸篮，注意不要超过炸篮标注的最大刻度线，食物堆叠过高会影响热风循环效果。关上炸篮后，
            旋转旋钮设定温度与时间，温度范围通常为八十至二百度。设备工作时外壳会有一定发热，属于正常现象。
            烹饪过程中可拉出炸篮查看食物状态，此时设备会自动停止加热，推回后继续运行。如上所述，热风循环依赖
            腔体内气流畅通，因此每次烹饪前都应确认炸篮是否推到位。

            第四章 清洁与保养

            每次使用完毕，待设备完全冷却后再进行清洁。清洗时请勿将机身浸入水中，仅可用湿布擦拭外壳。炸篮和
            内胆可用温水和软海绵清洗，清洗后请彻底晾干再装回，避免残留水分影响下次使用。长期不使用时，请将
            电源线收纳于机身底部的绕线槽内，并置于干燥通风处存放。
            """;

    // ==================== 清洗 ====================

    @Test
    @DisplayName("cleanText：null 和空串安全返回")
    void cleanTextHandlesNullAndBlank() {
        assertEquals("", KbChunking.cleanText(null));
        assertEquals("", KbChunking.cleanText("   \n\n  "));
    }

    @Test
    @DisplayName("cleanText：CRLF 统一成 LF（用空行分隔的两段来验证，否则会先被折行规则合并）")
    void cleanTextNormalizesLineEndings() {
        // 注意：相邻两行之间没有空行时，属于"排版折行"，会被下面那条规则合并掉，
        // 所以测 CRLF 必须用真正的段落分隔（空行）。
        assertEquals("第一行\n\n第二行", KbChunking.cleanText("第一行\r\n\r\n第二行"));
    }

    @Test
    @DisplayName("cleanText：行内折行被删除（PDF 排版换行不能进正文）")
    void cleanTextRemovesSoftLineBreaks() {
        // 前后都有非空白字符的单换行 = 排版折行，必须拼回一句
        assertEquals("产品采用热风循环技术，通过高速气流形成对流。",
                KbChunking.cleanText("产品采用热风循环技术，\n通过高速气流形成对流。"));
    }

    @Test
    @DisplayName("cleanText：段落之间的空行保留，3 个以上连续换行压成 1 个")
    void cleanTextKeepsParagraphBreaks() {
        assertEquals("第一段\n\n第二段", KbChunking.cleanText("第一段\n\n\n\n\n第二段"));
    }

    // ==================== 分块 ====================

    @Test
    @DisplayName("split：空内容切不出块，不会抛异常")
    void splitBlankReturnsEmpty() {
        assertTrue(KbChunking.split(null).isEmpty());
        assertTrue(KbChunking.split("   ").isEmpty());
    }

    @Test
    @DisplayName("split：短文档只有一块")
    void splitShortTextIntoOneChunk() {
        List<String> chunks = KbChunking.split("这是一段很短的话，不该被切开。");
        assertEquals(1, chunks.size());
    }

    @Test
    @DisplayName("split：长文档切成多块，且每块都不为空")
    void splitLongTextIntoMultipleChunks() {
        List<String> chunks = KbChunking.split(KbChunking.cleanText(MANUAL));
        assertTrue(chunks.size() > 1, "300 token 上限下这篇说明书应该被切成多块，实际 " + chunks.size() + " 块");
        chunks.forEach(c -> assertFalse(c.isBlank(), "出现了空白块"));
    }

    @Test
    @DisplayName("split：★ 内容不丢——所有块拼回去等于原文（忽略空白差异）")
    void splitDoesNotLoseContent() {
        String cleaned = KbChunking.cleanText(MANUAL);
        List<String> chunks = KbChunking.split(cleaned);

        String joined = String.join("", chunks).replaceAll("\\s", "");
        String expected = cleaned.replaceAll("\\s", "");

        assertEquals(expected, joined, "分块把正文弄丢或弄重了");
    }

    @Test
    @DisplayName("split：chunkSize 越小，块数越多")
    void smallerChunkSizeProducesMoreChunks() {
        String cleaned = KbChunking.cleanText(MANUAL);
        int big = KbChunking.split(cleaned, 300).size();
        int small = KbChunking.split(cleaned, 100).size();

        assertTrue(small >= big, "chunkSize=100 应切出不少于 chunkSize=300 的块数，实际 %d vs %d".formatted(small, big));
    }

    @Test
    @DisplayName("split：清洗后再分块，块里不应残留排版折行")
    void chunksHaveNoSoftLineBreaks() {
        List<String> chunks = KbChunking.split(KbChunking.cleanText(MANUAL));
        assertTrue(chunks.stream().noneMatch(c -> c.matches("(?s).*\\S\\n\\S.*")),
                "块内仍存在单换行，说明清洗没有生效");
    }

    // ==================== 尾块合并 ====================

    @Test
    @DisplayName("mergeShortTail：尾块过短 → 并进前一块，块数减一")
    void mergeShortTailMergesTinyLastChunk() {
        List<Document> chunks = new java.util.ArrayList<>(List.of(
                new Document("A".repeat(200)),
                new Document("B".repeat(200)),
                new Document("尾巴")));           // 2 个字符 < 50

        KbChunking.mergeShortTail(chunks);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(1).getText().endsWith("尾巴"));
    }

    @Test
    @DisplayName("mergeShortTail：尾块够长 → 不动")
    void mergeShortTailKeepsLongLastChunk() {
        List<Document> chunks = new java.util.ArrayList<>(List.of(
                new Document("A".repeat(200)),
                new Document("B".repeat(100))));  // 100 个字符 >= 50

        KbChunking.mergeShortTail(chunks);

        assertEquals(2, chunks.size());
    }

    @Test
    @DisplayName("mergeShortTail：只有一块时不合并，也不抛异常")
    void mergeShortTailSkipsSingleChunk() {
        List<Document> chunks = new java.util.ArrayList<>(List.of(new Document("短")));
        KbChunking.mergeShortTail(chunks);
        assertEquals(1, chunks.size());
    }

    @Test
    @DisplayName("mergeShortTail：合并后内容等于两块之和")
    void mergeShortTailPreservesContent() {
        List<Document> chunks = new java.util.ArrayList<>(List.of(
                new Document("前面的一大段内容"),
                new Document("尾巴")));

        KbChunking.mergeShortTail(chunks);

        assertEquals("前面的一大段内容尾巴", chunks.get(0).getText());
    }
}