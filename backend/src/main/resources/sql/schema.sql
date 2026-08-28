-- ==============================================================================
-- AgentForge 工业级多租户 AI 智能体编排与混合检索 RAG 平台 DDL
-- 数据库方言: PostgreSQL 16 + pgvector
-- ==============================================================================

-- 1. 启用向量扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. 租户表 (sys_tenant)
CREATE TABLE IF NOT EXISTS sys_tenant (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    code                VARCHAR(64) NOT NULL UNIQUE,
    wallet_balance      NUMERIC(12, 4) NOT NULL DEFAULT 100.0000, -- 租户钱包可用余额(元)
    white_label_config  JSONB DEFAULT '{"logo": "", "title": "AgentForge", "theme": "dark", "copyright": "© 2026 AgentForge Inc."}'::jsonb,
    status              SMALLINT NOT NULL DEFAULT 1, -- 1: 正常, 0: 禁用, 2: 欠费冻结
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tenant_code ON sys_tenant(code);

-- 3. 用户表 (sys_user)
CREATE TABLE IF NOT EXISTS sys_user (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    username            VARCHAR(64) NOT NULL,
    password_hash       VARCHAR(256) NOT NULL,
    salt                VARCHAR(64) NOT NULL,
    real_name           VARCHAR(64),
    email               VARCHAR(128),
    role                VARCHAR(32) NOT NULL DEFAULT 'EDITOR', -- OWNER, ADMIN, EDITOR, VIEWER
    status              SMALLINT NOT NULL DEFAULT 1, -- 1: 正常, 0: 禁用
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_username UNIQUE (tenant_id, username)
);
CREATE INDEX IF NOT EXISTS idx_user_tenant_id ON sys_user(tenant_id);

-- 4. API Key 表 (api_key)
CREATE TABLE IF NOT EXISTS api_key (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    name                VARCHAR(64) NOT NULL,
    key_prefix          VARCHAR(16) NOT NULL, -- 如 af-sk-abc
    key_hash            VARCHAR(128) NOT NULL UNIQUE,
    rate_limit_rpm      INT NOT NULL DEFAULT 60, -- 每分钟请求数限制
    rate_limit_tpm      INT NOT NULL DEFAULT 100000, -- 每分钟 Token 数限制
    status              SMALLINT NOT NULL DEFAULT 1, -- 1: 启用, 0: 禁用
    expired_at          TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_api_key_tenant ON api_key(tenant_id);
CREATE INDEX IF NOT EXISTS idx_api_key_hash ON api_key(key_hash);

-- 5. 知识库数据集表 (dataset)
CREATE TABLE IF NOT EXISTS dataset (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    name                VARCHAR(128) NOT NULL,
    description         TEXT,
    avatar              VARCHAR(256),
    embedding_model     VARCHAR(64) NOT NULL DEFAULT 'text-embedding-3-small',
    embedding_dim       INT NOT NULL DEFAULT 1536,
    chunk_size          INT NOT NULL DEFAULT 500,
    chunk_overlap       INT NOT NULL DEFAULT 50,
    status              SMALLINT NOT NULL DEFAULT 1, -- 1: 正常, 0: 隐藏/下线
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dataset_tenant ON dataset(tenant_id);

-- 6. 知识库文档明细表 (document)
CREATE TABLE IF NOT EXISTS document (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    dataset_id          BIGINT NOT NULL REFERENCES dataset(id) ON DELETE CASCADE,
    name                VARCHAR(256) NOT NULL,
    file_path           VARCHAR(512) NOT NULL,
    file_size           BIGINT NOT NULL DEFAULT 0,
    file_type           VARCHAR(32) NOT NULL, -- PDF, DOCX, MD, TXT
    char_count          INT NOT NULL DEFAULT 0,
    chunk_count         INT NOT NULL DEFAULT 0,
    parse_status        VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, PARSING, SUCCESS, FAILED
    error_msg           TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_document_dataset ON document(dataset_id);
CREATE INDEX IF NOT EXISTS idx_document_tenant ON document(tenant_id);

-- 7. 文档切片与高维向量表 (document_chunk) - 核心 RAG 混合检索表
CREATE TABLE IF NOT EXISTS document_chunk (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    dataset_id          BIGINT NOT NULL REFERENCES dataset(id) ON DELETE CASCADE,
    document_id         BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    chunk_index         INT NOT NULL,
    content             TEXT NOT NULL,
    embedding           vector(1536), -- pgvector 稠密向量
    token_count         INT NOT NULL DEFAULT 0,
    metadata            JSONB DEFAULT '{}'::jsonb, -- 包含段落序号、页码、原始标题层级等
    tsv_content         tsvector, -- BM25 全文检索分词向量
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 核心索引配置:
-- 7.1 HNSW 向量索引 (余弦距离搜索)
CREATE INDEX IF NOT EXISTS idx_chunk_embedding_hnsw 
ON document_chunk USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 7.2 全文检索 GIN 索引
CREATE INDEX IF NOT EXISTS idx_chunk_tsv 
ON document_chunk USING gin (tsv_content);

-- 7.3 租户与数据集复合索引
CREATE INDEX IF NOT EXISTS idx_chunk_tenant_dataset 
ON document_chunk(tenant_id, dataset_id);

-- 7.4 自动生成/同步 tsvector 的触发器函数
CREATE OR REPLACE FUNCTION update_chunk_tsv() RETURNS trigger AS $$
BEGIN
    NEW.tsv_content := to_tsvector('simple', COALESCE(NEW.content, ''));
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_chunk_tsv_update ON document_chunk;
CREATE TRIGGER trg_chunk_tsv_update
BEFORE INSERT OR UPDATE ON document_chunk
FOR EACH ROW EXECUTE FUNCTION update_chunk_tsv();

-- 8. 智能体应用表 (agent_app)
CREATE TABLE IF NOT EXISTS agent_app (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    name                VARCHAR(128) NOT NULL,
    description         TEXT,
    avatar              VARCHAR(256),
    app_type            VARCHAR(32) NOT NULL DEFAULT 'AGENT', -- CHATBOT, AGENT, WORKFLOW
    system_prompt       TEXT,
    model_config        JSONB DEFAULT '{"provider": "deepseek", "model": "deepseek-chat", "temperature": 0.7, "max_tokens": 2048}'::jsonb,
    tools_config        JSONB DEFAULT '[]'::jsonb, -- 启用的内置与外部 OpenAPI 工具列表
    dataset_ids         JSONB DEFAULT '[]'::jsonb, -- 关联知识库 ID 数组
    status              SMALLINT NOT NULL DEFAULT 1, -- 1: 启用, 0: 禁用
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_agent_app_tenant ON agent_app(tenant_id);

-- 9. 工作流 DAG 定义表 (workflow_definition)
CREATE TABLE IF NOT EXISTS workflow_definition (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    app_id              BIGINT NOT NULL REFERENCES agent_app(id) ON DELETE CASCADE,
    name                VARCHAR(128) NOT NULL,
    description         TEXT,
    dag_json            JSONB NOT NULL DEFAULT '{"nodes": [], "edges": []}'::jsonb,
    version             INT NOT NULL DEFAULT 1,
    status              SMALLINT NOT NULL DEFAULT 1,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_workflow_app ON workflow_definition(app_id);
CREATE INDEX IF NOT EXISTS idx_workflow_tenant ON workflow_definition(tenant_id);

-- 10. 工作流执行实例表 (workflow_execution)
CREATE TABLE IF NOT EXISTS workflow_execution (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    workflow_id         BIGINT NOT NULL REFERENCES workflow_definition(id) ON DELETE CASCADE,
    app_id              BIGINT NOT NULL,
    trigger_type        VARCHAR(32) NOT NULL DEFAULT 'API', -- API, STUDIO, WEBHOOK, SCHEDULE
    status              VARCHAR(32) NOT NULL DEFAULT 'RUNNING', -- RUNNING, SUCCESS, FAILED, TIMEOUT
    input_snapshot      JSONB DEFAULT '{}'::jsonb,
    output_snapshot     JSONB DEFAULT '{}'::jsonb,
    step_details        JSONB DEFAULT '[]'::jsonb, -- 包含每个节点的执行耗时、输入输出
    total_duration_ms   BIGINT DEFAULT 0,
    error_msg           TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_execution_workflow ON workflow_execution(workflow_id);
CREATE INDEX IF NOT EXISTS idx_execution_tenant ON workflow_execution(tenant_id);

-- 11. Token 计量审计与计费流水表 (token_usage_log)
CREATE TABLE IF NOT EXISTS token_usage_log (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    app_id              BIGINT,
    user_id             BIGINT,
    api_key_id          BIGINT,
    model_name          VARCHAR(64) NOT NULL,
    prompt_tokens       INT NOT NULL DEFAULT 0,
    completion_tokens   INT NOT NULL DEFAULT 0,
    total_tokens        INT NOT NULL DEFAULT 0,
    is_cached           BOOLEAN NOT NULL DEFAULT FALSE, -- 是否命中向量语义缓存
    cost_amount         NUMERIC(10, 6) NOT NULL DEFAULT 0.000000, -- 消费金额(元)
    duration_ms         BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_token_log_tenant ON token_usage_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_token_log_created_at ON token_usage_log(created_at);

-- 12. 多轮对话会话表 (chat_session)
CREATE TABLE IF NOT EXISTS chat_session (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    app_id              BIGINT NOT NULL,
    title               VARCHAR(255) NOT NULL,
    message_count       INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_session_tenant_user ON chat_session(tenant_id, user_id);

-- 13. 对话消息明细表 (chat_message)
CREATE TABLE IF NOT EXISTS chat_message (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    session_id          BIGINT NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role                VARCHAR(32) NOT NULL, -- user, assistant, system
    content             TEXT NOT NULL,
    prompt_tokens       INT DEFAULT 0,
    completion_tokens   INT DEFAULT 0,
    total_tokens        INT DEFAULT 0,
    citations_json      TEXT, -- RAG 原文溯源引用快照
    reasoning_steps_json TEXT, -- ReAct 思考推理步骤快照
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_msg_session ON chat_message(session_id);

-- 14. 问答质量评价与 Bad Case 语料反馈表 (chat_feedback)
CREATE TABLE IF NOT EXISTS chat_feedback (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES sys_tenant(id) ON DELETE CASCADE,
    message_id          BIGINT NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL,
    rating              INT NOT NULL, -- 1: 点赞, -1: 点踩
    feedback_type       VARCHAR(64), -- 答非所问, 幻觉错误, 格式错乱, 缺乏依据
    comment             TEXT,
    is_resolved         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_feedback_tenant ON chat_feedback(tenant_id);
CREATE INDEX IF NOT EXISTS idx_feedback_message ON chat_feedback(message_id);

