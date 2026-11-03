package org.example.springaitest.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感词 DFA 引擎（P0：精确 + 最长匹配 + 不重叠 + 归一化绕过）
 */
@Service
public class SensitiveWordEngine {

    // ===================== 1. 数据契约 =====================
    public enum Action { BLOCK, MASK, REVIEW }

    public record Rule(String word, String category, Action action) {}

    /** start/end 都是【原串】下标，左闭右开 */
    public record Hit(String word, String category, Action action, int start, int end) {
        @Override public String toString() {
            return "%s(%s/%s) [%d,%d)".formatted(word, category, action, start, end);
        }
    }

    public record ScanResult(String masked, boolean blocked, List<Hit> hits) {}

    // ===================== 2. 树节点 =====================
    private static class Node {
        Map<Character, Node> children = new HashMap<>();
        Rule rule;          // 非 null 表示：从根走到这里，是一个完整的词
    }

    /** volatile + 整体替换，保证 rebuild 与并发 scan 安全 */
    private volatile Node root = new Node();

    // ===================== 3. 建树 =====================
    public void rebuild(List<Rule> rules) {
        Node newRoot = new Node();
        for (Rule rule : rules) {
            //  3-1: 归一化词本身（和文本用同一个 normalize）
            Normalized normalize = normalize(rule.word);
            //           Normalized n = normalize(rule.word());
            //  3-2: 从 newRoot 出发逐字往下建节点，走到底把 rule 盖在最后一个节点上
            Node node = newRoot;
            for (int i = 0; i < normalize.text.length(); i++) {
                char c = normalize.text.charAt(i);
                node.children.putIfAbsent(c, new Node());
                node = node.children.get(c);
            }
            node.rule = rule;
        }
        this.root = newRoot;   // 建完再整体替换
    }

    // ===================== 4. 扫描 =====================
    public ScanResult scan(String raw) {
        Normalized n = normalize(raw);
        String text = n.text();
        int[] map = n.map();

        List<Hit> hits = new ArrayList<>();

        //  4-1: 外层 for：i 从 0 到 text.length()-1，每个位置都试一次
        for (int i = 0; i < text.length(); i++) {
            Node node = root;
            int j=i;
            int bestEnd = -1;
            Rule bestRule=null;

            //  4-2: 内层 while：沿树往下走
            //          - 走不通立刻停
            //          - 每走到一个 node.rule != null 的节点，就更新 bestEnd / bestRule（贪心取最长）
            //  4-3: 走出结果后，把 [i, bestEnd) 映射回原串：
            //              int start = map[i];
            //              int end   = map[bestEnd - 1] + 1;
            //           加入 hits
            //  4-4: i = bestEnd - 1;   // 跳到末尾（注意 -1）

            while (j<text.length()){
                Node next = node.children.get(text.charAt(j));
                if (next == null) break;
                node = next;//确定非空往下走
                j++;
                if (next.rule != null) {
                    //开始更新结束位置
                    bestEnd = j;
                    bestRule = next.rule;
                }
            }
            if(bestRule != null){
                int start=map[i];
                int end=map[bestEnd-1]+1;  //映射回原串
                hits.add(new Hit(bestRule.word, bestRule.category,bestRule.action, start, end));
                i = bestEnd - 1;  // 跳到末尾（注意 -1）

            }
        }


        return new ScanResult(mask(raw, hits), hasBlock(hits), hits);
    }

    // ===================== 5. 归一化（本课重点） =====================
    /** text = 洗净后的串；map[k] = text 第 k 个字对应的原串下标 */
    private record Normalized(String text, int[] map) {}

    private Normalized normalize(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        int[] map = new int[input.length()];

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // 5-1: 判断这个字要不要丢掉（不写进 sb、也不写 map）：
            //   - 空白：' ' '\t' '\n' '\r' 以及全角空格 '\u3000'
            //   - 零宽字符：'\u200B' '\u200C' '\u200D' '\uFEFF'
            //   - 常见分隔：'.' ',' '*' '-' '_' '|' '/' '\\' '@' '#'
            //   -> continue
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u3000' ||
                    c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF' ||
                    c == '.' || c == ',' || c == '*' || c == '-' || c == '_' || c == '|' ||
                    c == '/' || c == '\\' || c == '@' || c == '#') {
                continue;
            }

            //  5-2: 全角转半角
            //   全角 A-Z：'\uFF21'~'\uFF3A'  -> 减 0xFEE0
            //   全角 a-z：'\uFF41'~'\uFF5A'  -> 减 0xFEE0
            //   全角 0-9：'\uFF10'~'\uFF19'  -> 减 0xFEE0

            if (c >= '\uFF21' && c <= '\uFF3A') {
                c = (char) (c - 0xFEE0);
            } else if (c >= '\uFF41' && c <= '\uFF5A') {
                c = (char) (c - 0xFEE0);
            } else if (c >= '\uFF10' && c <= '\uFF19') {
                c = (char) (c - 0xFEE0);
            }
            // 5-3: 字母统一转小写  Character.toLowerCase(c)
            c = Character.toLowerCase(c);
            //  5-4: 留下的字写进 sb，并记录它来自原串哪个下标：
            sb.append(c);
            map[sb.length() - 1] = i;
        }
        return new Normalized(sb.toString(), Arrays.copyOf(map, sb.length()));
    }

    // ===================== 6. 打码 & 判定（已给，不用改） =====================
    private String mask(String raw, List<Hit> hits) {
        if (hits.isEmpty()) return raw;
        char[] chars = raw.toCharArray();
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (Hit h : hits) {
            if (h.start() < cursor) continue;          // 防御：不重叠
            out.append(chars, cursor, h.start() - cursor).append("***");
            cursor = h.end();
        }
        out.append(chars, cursor, chars.length - cursor);
        return out.toString();
    }

    private boolean hasBlock(List<Hit> hits) {
        return hits.stream().anyMatch(h -> h.action() == Action.BLOCK);
    }

    // ===================== 7. 自测入口 =====================
    /*public static void main(String[] args) {
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.rebuild(List.of(
                new Rule("赌", "违法", Action.BLOCK),
                new Rule("赌博", "违法", Action.BLOCK),
                new Rule("赌博网站", "违法", Action.BLOCK),
                new Rule("aa", "重复", Action.REVIEW)
        ));
        System.out.println(engine.scan("赌博网站"));   // 期望：只报 1 次，是「赌博网站」[0,4)，不是「赌」
        System.out.println(engine.scan("aaaa"));       // 期望：2 次命中 [0,2) 和 [2,4)，不是 3 次
        System.out.println(engine.scan("今天天气不错")); // 期望：hits=[]，masked 原样返回
    }*/
}