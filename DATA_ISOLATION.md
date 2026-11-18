# Database migrations

The application uses the existing `springai` MySQL database for user accounts, soup games, chat sessions, and Spring AI chat messages. Flyway applies versioned migrations from `src/main/resources/db/migration` at startup and records applied versions in `flyway_schema_history`.

## Existing database

Back up the database before starting the updated application. `spring.flyway.baseline-on-migrate=true` with baseline version `2` marks a non-empty database without Flyway history as version 2, so Flyway does not rerun the manually applied user-isolation migration. Flyway then applies V3 to ensure the Spring AI message table exists. This assumes the existing database already has the `ai_session` and user-isolation schema from V2.

If the schema has not had V2 applied yet, apply `V2__user_data_isolation.sql` once before launching the app, or change the baseline procedure to match the actual database. Do not run the same DDL manually after Flyway records it as applied.

## New database

Create an empty `springai` database. On startup, Flyway applies V1, V2, and V3 in order to create the base tables, user-isolation tables/columns, and chat-memory message table.

Set `MYSQL_PASSWORD`; optionally set `MYSQL_HOST`, `MYSQL_PORT`, and `MYSQL_DATABASE`. Chat messages are stored in `SPRING_AI_CHAT_MEMORY` in this same database, keyed by `conversation_id`. `ai_session` and `SOUP_GAME` hold ownership and session/game metadata; they are not duplicate message tables.

An older SQL helper, `move_legacy_chat_memory.sql`, is retained as historical material and is not a Flyway migration. Do not run it as part of this single-database setup.
