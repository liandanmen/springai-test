package org.example.springaitest.test;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;

public class SplitProbe {

    /** 故意写成"说明书"的样子：有小标题、有长段落、有"如上所述"这种指代 */
    static final String MANUAL = """
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

            第五章 常见问题

            一、设备不加热：请检查插头是否插紧、旋钮是否已设定温度与时间。
            二、食物上色不均：多为食材堆叠过密所致，建议减少单次用量并适当翻动。
            三、运行中发出异响：请检查是否有异物掉入腔体。
            四、按键无反应：请断开电源静置一分钟后再重新通电。
            """;

    public static void main(String[] args) {
        // 创建分块器
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(300)             // 每块最多 300 个 token
                .withMinChunkSizeChars(100)     // 太短的块尝试与后一块合并
                .withMinChunkLengthToEmbed(5)   // 少于 5 个字符的块直接丢弃
                .withKeepSeparator(true)        // 保留换行等分隔符
                .withPunctuationMarks(List.of('。', '！', '？', '；', '：', '\n'))    // 指定分隔符
                .build();

        // 分块
        String cleanText = cleanText(MANUAL);
        List<Document> chunks = new ArrayList<>(splitter.split(new Document(cleanText)));
        if(chunks.size() > 1){
            Document last = chunks.get(chunks.size() - 1);
            // 尾块太短 → 并到前一块，而不是丢弃
            if(last.getText().trim().length() < 50){
                Document prev = chunks.get(chunks.size() - 2);
                Document merged = new Document(
                        prev.getId(),
                        prev.getText() + last.getText(),      // 拼起来
                        prev.getMetadata());
                chunks.set(chunks.size() - 2, merged);
                chunks.remove(chunks.size() - 1);
            }

        }

        System.out.println("原文 = " + MANUAL.length() + " 字符");
        System.out.println("切出 = " + chunks.size() + " 块");
        System.out.println("================");

        for (int i = 0; i < chunks.size(); i++) {
            String t = chunks.get(i).getText();
            System.out.println("[" + i + "] 字符数=" + t.length() + "  id=" + chunks.get(i).getId().substring(0, 8));
            System.out.println("    开头: " + head(t, 45));
            System.out.println("    结尾: " + tail(t, 35));
            System.out.println();
        }
    }

    static String head(String s, int n) {
        String x = s.replace("\n", "⏎");
        return x.length() <= n ? x : x.substring(0, n) + "...";
    }

    static String tail(String s, int n) {
        String x = s.replace("\n", "⏎");
        return x.length() <= n ? x : "..." + x.substring(x.length() - n);
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