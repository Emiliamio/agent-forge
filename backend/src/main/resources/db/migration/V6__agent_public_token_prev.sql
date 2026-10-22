-- V6: 为 agent_app 表增加 public_token_prev 列以支持挂件密钥平滑双阶段轮换
ALTER TABLE agent_app ADD COLUMN IF NOT EXISTS public_token_prev VARCHAR(64);
