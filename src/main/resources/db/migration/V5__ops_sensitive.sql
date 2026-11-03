-- 敏感词规则表 + 初始词库
-- 注意：种子数据用普通 INSERT，请勿手工重复执行本文件（会撞 uk_word 唯一索引）
create table if not exists sensitive_rule
(
    id         bigint      not null auto_increment primary key,
    word       varchar(64) not null comment '敏感词',
    category   varchar(16) not null comment '敏感词类别',
    action     varchar(16) not null comment 'BLOCK/MASK/REVIEW',
    enable     tinyint(1)  not null default 1 comment '是否启用',
    created_at timestamp   not null default current_timestamp,
    updated_at timestamp   not null default current_timestamp on update current_timestamp,
    unique key uk_word (word)
) engine = InnoDB
  default charset = utf8mb4;

insert into sensitive_rule (word, category, action)
values ('色情', '涉黄', 'BLOCK'),
       ('法轮功', '涉政', 'BLOCK'),
       ('赌博', '涉黄涉赌', 'BLOCK'),
       ('傻逼', '辱骂', 'MASK'),
       ('去死', '辱骂', 'MASK'),
       ('妈的', '脏话', 'MASK'),
       ('垃圾游戏', '负面情绪', 'REVIEW'),
       ('弱智', '人身攻击', 'REVIEW');
