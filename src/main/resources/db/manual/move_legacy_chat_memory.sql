INSERT INTO springai_context.SPRING_AI_CHAT_MEMORY
    (conversation_id, content, type, timestamp, sequence_id)
SELECT conversation_id, content, type, timestamp, sequence_id
FROM springai.SPRING_AI_CHAT_MEMORY;
