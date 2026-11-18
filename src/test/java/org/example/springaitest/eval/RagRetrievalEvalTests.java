package org.example.springaitest.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.springaitest.utils.KbChunking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 检索效果评测。
 *
 * <p>为什么需要它：分块大小、topK、相似度阈值这三个参数如果只是"调着感觉还行"，
 * 面试官一问就露馅。这组评测用 15 条标注问题，量化不同参数下的检索命中率。
 *
 * <p>指标定义：
 * <ul>
 *   <li>hit@1：召回结果第 1 条就包含期望关键词</li>
 *   <li>hit@3：前 3 条里任一条包含期望关键词</li>
 *   <li>空召回：阈值过高或语义不匹配，一条都没召回（此时用户会得到"未找到相关内容"）</li>
 * </ul>
 *
 * <p>运行方式（需要环境变量 OPENAI_API_KEY）：
 * <pre>mvn test -Dtest=RagRetrievalEvalTests</pre>
 * 没设 key 时自动跳过，不会影响正常的 mvn test。
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RagRetrievalEvalTests {

    /** 和 application.yaml 里 spring.ai.openai.base-url 保持一致，可用环境变量覆盖 */
    private static final String BASE_URL = System.getenv().getOrDefault(
            "OPENAI_BASE_URL",
            "https://ws-oxshmqxdh9f1jmrs.cn-beijing.maas.aliyuncs.com/compatible-mode/v1");
    private static final String EMBEDDING_MODEL = System.getenv().getOrDefault(
            "EMBEDDING_MODEL", "text-embedding-v4");

    /** 语料：3 篇文档，共 15 个可标注章节 */
    private static final List<String> CORPUS = List.of("airfryer.txt", "company.txt", "faq.txt");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    record Config(String name, int chunkSize, int topK, double threshold) {}

    record Question(String text, String keyword) {}

    record Chunk(String doc, int index, String text, float[] vec) {}

    record Scored(Chunk chunk, double score) {}

    @Test
    @DisplayName("对比 4 组参数的分块与检索效果")
    void compareRetrievalConfigs() throws Exception {
        List<Question> questions = loadQuestions();
        float[][] questionVecs = embed(questions.stream().map(Question::text).toList());

        List<Config> configs = List.of(
                new Config("A 现状 300/5/0.30", 300, 5, 0.30),
                new Config("B 300/3/0.30", 300, 3, 0.30),
                new Config("C 500/5/0.30", 500, 5, 0.30),
                new Config("D 300/5/0.40", 300, 5, 0.40));

        Map<Integer, List<Chunk>> chunkCache = new HashMap<>();
        StringBuilder table = new StringBuilder();
        table.append("\n| 配置 | 块数 | 平均召回 | hit@1 | hit@3 | 空召回 |\n");
        table.append("|---|---|---|---|---|---|\n");

        double baselineHit3 = 0;

        for (Config config : configs) {
            if (!chunkCache.containsKey(config.chunkSize())) {
                chunkCache.put(config.chunkSize(), buildChunks(config.chunkSize()));
            }
            List<Chunk> chunks = chunkCache.get(config.chunkSize());

            int hit1 = 0;
            int hit3 = 0;
            int empty = 0;
            int recalled = 0;
            List<String> missed = new ArrayList<>();

            for (int i = 0; i < questions.size(); i++) {
                Question q = questions.get(i);
                List<Scored> ranked = rank(chunks, questionVecs[i], config);

                recalled += ranked.size();
                if (ranked.isEmpty()) empty++;

                boolean first = !ranked.isEmpty() && ranked.get(0).chunk().text().contains(q.keyword());
                boolean inTop3 = ranked.stream()
                        .limit(3)
                        .anyMatch(s -> s.chunk().text().contains(q.keyword()));

                if (first) hit1++;
                if (inTop3) hit3++;
                else missed.add(q.text());
            }

            double r1 = hit1 * 100.0 / questions.size();
            double r3 = hit3 * 100.0 / questions.size();
            if (config.name().startsWith("A")) baselineHit3 = r3;

            table.append("| %s | %d | %.1f | %.0f%% (%d/%d) | %.0f%% (%d/%d) | %d |\n".formatted(
                    config.name(), chunks.size(), recalled * 1.0 / questions.size(),
                    r1, hit1, questions.size(), r3, hit3, questions.size(), empty));

            if (!missed.isEmpty()) {
                table.append("| ↑ 未进前 3 的问题：").append(String.join(" / ", missed)).append(" ||||||\n");
            }
        }

        System.out.println(table);

        // 底线断言：现状配置在自建语料上 hit@3 至少 70%，否则说明参数已经跑偏
        assertTrue(baselineHit3 >= 70, "现状配置 hit@3 只有 " + baselineHit3 + "%，低于 70% 底线");
    }

    // ==================== 分块 ====================

    private List<Chunk> buildChunks(int chunkSize) throws Exception {
        List<Chunk> result = new ArrayList<>();
        List<String> texts = new ArrayList<>();

        for (String doc : CORPUS) {
            String raw = readResource("/eval/corpus/" + doc);
            List<String> pieces = KbChunking.split(KbChunking.cleanText(raw), chunkSize);
            for (int i = 0; i < pieces.size(); i++) {
                texts.add(pieces.get(i));
                result.add(new Chunk(doc, i, pieces.get(i), null));
            }
        }

        float[][] vectors = embed(texts);
        List<Chunk> withVec = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            Chunk c = result.get(i);
            withVec.add(new Chunk(c.doc(), c.index(), c.text(), vectors[i]));
        }
        return withVec;
    }

    // ==================== 检索 ====================

    private List<Scored> rank(List<Chunk> chunks, float[] questionVec, Config config) {
        return chunks.stream()
                .map(c -> new Scored(c, cosine(questionVec, c.vec())))
                .filter(s -> s.score() >= config.threshold())
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(config.topK())
                .toList();
    }

    /** 余弦相似度：和 SimpleVectorStore 默认的度量方式一致 */
    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // ==================== 数据加载 ====================

    private List<Question> loadQuestions() throws Exception {
        List<Question> questions = new ArrayList<>();
        for (String line : readResource("/eval/questions.tsv").split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] parts = trimmed.split("\t");
            if (parts.length < 2) {
                throw new IllegalStateException("评测集格式错误（需要 问题<TAB>关键词）：" + line);
            }
            questions.add(new Question(parts[0].trim(), parts[1].trim()));
        }
        return questions;
    }

    private String readResource(String path) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("找不到资源：" + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ==================== Embedding ====================

    /** 批量调用 embedding 接口，每批 10 条，避免单请求过大 */
    private float[][] embed(List<String> inputs) throws Exception {
        float[][] vectors = new float[inputs.size()][];
        int batchSize = 10;

        for (int from = 0; from < inputs.size(); from += batchSize) {
            int to = Math.min(from + batchSize, inputs.size());
            List<String> batch = inputs.subList(from, to);

            Map<String, Object> body = Map.of("model", EMBEDDING_MODEL, "input", batch);
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/embeddings"))
                    .header("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("embedding 调用失败 HTTP " + response.statusCode() + "：" + response.body());
            }

            JsonNode data = json.readTree(response.body()).get("data");
            for (JsonNode item : data) {
                int index = from + item.get("index").asInt();
                JsonNode array = item.get("embedding");
                float[] vec = new float[array.size()];
                for (int i = 0; i < array.size(); i++) vec[i] = (float) array.get(i).asDouble();
                vectors[index] = vec;
            }
        }
        return vectors;
    }
}