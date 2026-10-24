-- V7__widget_call_audit.sql
-- 公开挂件调用审计表：记录调用者行为（IP/UA 仅存哈希）、结果状态及耗时
-- 架构说明：本表属于平台级审计日志（记录未登录外部访客对公共挂件的访问行为），不启用租户物理隔离过滤。

CREATE TABLE IF NOT EXISTS widget_call_audit (
    id           BIGSERIAL PRIMARY KEY,
    trace_id     VARCHAR(64)  NOT NULL,
    agent_id     BIGINT       NOT NULL,
    ip_hash      VARCHAR(64)  NOT NULL,      -- SHA-256(salt + ip)，不存明文
    ua_hash      VARCHAR(64)  NOT NULL,      -- SHA-256(ua)
    outcome      VARCHAR(16)  NOT NULL,      -- ok | rate_limited | replay_blocked | invalid_token | bad_request | error
    http_status  INTEGER      NOT NULL,
    latency_ms   INTEGER      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_widget_audit_created ON widget_call_audit (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_widget_audit_agent   ON widget_call_audit (agent_id, created_at DESC);

COMMENT ON TABLE widget_call_audit IS '公开挂件调用审计：记录谁在何时调用了哪个 agent 及结果（IP/UA 仅存哈希）。本表属平台级审计，不启用租户过滤。';
