CREATE TABLE ai_session (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  conversation_id VARCHAR(36) NOT NULL,
  title VARCHAR(128) NOT NULL DEFAULT '新会话',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_ai_session_conversation UNIQUE (conversation_id),
  INDEX idx_ai_session_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE SOUP_GAME
  ADD COLUMN user_id BIGINT NULL,
  ADD COLUMN conversation_id VARCHAR(36) NULL,
  ADD INDEX idx_soup_game_user_created (user_id, game_id),
  ADD UNIQUE INDEX uk_soup_game_conversation (conversation_id);
