-- ==============================================================================
-- AgentForge 演示与初始化种子数据 (seed-data.sql)
-- ==============================================================================

-- 1. 插入默认演示租户
INSERT INTO sys_tenant (id, name, code, wallet_balance, white_label_config, status)
VALUES (
    1,
    'AgentForge 官方演示租户',
    'tenant_default',
    1000.0000,
    '{"logo": "https://img.icons8.com/isometric/512/bot.png", "title": "AgentForge 企业智能中台", "theme": "dark", "copyright": "© 2026 AgentForge AI Studio"}'::jsonb,
    1
) ON CONFLICT (id) DO NOTHING;

-- 2. 插入系统管理员用户 (密码明文: admin123, 采用 BCrypt 格式或安全哈希)
-- 密码哈希: $2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIkeHG2 (admin123)
INSERT INTO sys_user (id, tenant_id, username, password_hash, salt, real_name, email, role, status)
VALUES (
    1,
    1,
    'admin',
    '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIkeHG2',
    'af_salt_888',
    '系统管理员',
    'admin@agentforge.ai',
    'OWNER',
    1
) ON CONFLICT (id) DO NOTHING;

-- 3. 插入演示专属 API Key (Key明文: af-sk-demo-master-key-2026, 前缀: af-sk-demo)
INSERT INTO api_key (id, tenant_id, name, key_prefix, key_hash, rate_limit_rpm, rate_limit_tpm, status)
VALUES (
    1,
    1,
    '全功能体验密钥',
    'af-sk-demo',
    'e9b986b6a03d08f3708e3d06a74be8bc4f52fa4b7ee2aa4e3ffb0c53e839e24b', -- SHA256 of demo key
    120,
    200000,
    1
) ON CONFLICT (id) DO NOTHING;

-- 4. 插入示例知识库
INSERT INTO dataset (id, tenant_id, name, description, avatar, embedding_model, embedding_dim, chunk_size, chunk_overlap, status)
VALUES (
    1,
    1,
    '企业数字化与技术规范知识库',
    '包含企业通用技术架构规范、代码规范与研发流程指南',
    'https://img.icons8.com/isometric/512/database.png',
    'text-embedding-3-small',
    1536,
    500,
    50,
    1
) ON CONFLICT (id) DO NOTHING;

-- 5. 插入示例智能体应用
INSERT INTO agent_app (id, tenant_id, name, description, avatar, app_type, system_prompt, model_config, tools_config, dataset_ids, status)
VALUES (
    1,
    1,
    '企业研发架构助手 (ForgeBot)',
    '具备混合检索 RAG 与系统工具调用能力的专业级研发助手',
    'https://img.icons8.com/isometric/512/artificial-intelligence.png',
    'AGENT',
    '你是一个专业的企业级架构师与技术研发助手，请结合企业知识库与系统工具，严谨、专业、结构化地回答用户的问题。回答时请准确标明引用来源。',
    '{"provider": "deepseek", "model": "deepseek-chat", "temperature": 0.7, "max_tokens": 4096}'::jsonb,
    '["web_search", "calculator", "current_time"]'::jsonb,
    '[1]'::jsonb,
    1
) ON CONFLICT (id) DO NOTHING;

-- 同步序列
SELECT setval('sys_tenant_id_seq', (SELECT MAX(id) FROM sys_tenant));
SELECT setval('sys_user_id_seq', (SELECT MAX(id) FROM sys_user));
SELECT setval('api_key_id_seq', (SELECT MAX(id) FROM api_key));
SELECT setval('dataset_id_seq', (SELECT MAX(id) FROM dataset));
SELECT setval('agent_app_id_seq', (SELECT MAX(id) FROM agent_app));
