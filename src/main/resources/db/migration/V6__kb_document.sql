create table if not exists kb_document
(
    id         bigint       not null auto_increment primary key,
    user_id    bigint       not null,
    file_name  varchar(255) not null comment '文件名',
    char_count int          not null comment '记录原文多少字',
    chunk_count int not null default 0 comment '文档切了几块',
    status     varchar(16)  not null default 'INDEX' comment 'INDEX/FAILED',
    created_at timestamp    not null default current_timestamp,
    updated_at timestamp    not null default current_timestamp on update current_timestamp
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

create table if not exists kb_chunk
(
    id          bigint      not null auto_increment primary key,
    document_id bigint      not null,
    user_id     bigint      not null,
    chunk_index int         not null comment '文档第几块',
    content     text        not null comment '文档块内容',
    vector_id   varchar(64) DEFAULT null comment '向量id',
    created_at  timestamp   not null default current_timestamp
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='文档块表';

# 创建索引，文档表user_id,文档块表user_id,document_id
create index idx_kb_document_user_id on kb_document (user_id);
create index idx_kb_chunk_user_id_document_id on kb_chunk (user_id, document_id);


