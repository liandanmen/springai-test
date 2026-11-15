package org.example.springaitest.config;


import org.example.springaitest.constant.KbPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.MysqlChatMemoryRepositoryDialect;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class CommonCongiguraton {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    //存储层：JDBC 存储
    @Bean
    public ChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new MysqlChatMemoryRepositoryDialect())
                .build();
    }

    //记忆层：滑动窗口，只保留最近 10 条
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }

    //普通聊天：带记忆
    @Bean
    public ChatClient chatClient(OpenAiChatModel mm, ChatMemory chatMemory) {
        return ChatClient.builder(mm)
                .defaultSystem("你是一个沉稳靠谱，不说废话的助手,叫做张德帅")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor
                                .builder(chatMemory)
                                .build()
                )
                .build();
    }

    /*
     * 海龟汤主持：不要挂 QuestionAnswerAdvisor！
     * 项目里唯一的 VectorStore 是文档知识库（opsVectorStore），
     * 一旦挂上，玩家问"是谋杀吗"就会去检索用户上传的文档 —— 既是串味，也是跨功能数据泄露。
     * 海龟汤的"知识"全部来自 HOST_PROMPT + 谜底，不需要向量检索。
     */
    @Bean(name = "soupChatClient")
    public ChatClient soupChatClient(OpenAiChatModel mm) {
        return ChatClient.builder(mm)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /*
     * 文档问答：不带 QuestionAnswerAdvisor
     * 草稿检索由 DocumentIndexServiceImpl.search() 手工完成（要带 userId 过滤），
     * advisor 再检索一次既浪费又绕过了租户隔离。
     */
    @Bean(name = "kbAnswerClient")
    public ChatClient kbAnswerClient(OpenAiChatModel mm) {
        return ChatClient.builder(mm)
                .defaultSystem(KbPrompt.KB_SYSTEM)
                .build();
    }
}