# 🚀 AgentForge (灵眸智枢) —— 纯血 Java 21 企业级 AI Agent 智能体与混合 RAG 中台

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg?style=flat&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.4-emerald.svg?style=flat&logo=vuedotjs)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-blue.svg?style=flat&logo=postgresql)](https://github.com/pgvector/pgvector)
[![Redis](https://img.shields.io/badge/Redis-7.2-red.svg?style=flat&logo=redis)](https://redis.io/)
[![CI/CD Pipeline](https://github.com/Emiliamio/agent-forge/actions/workflows/ci.yml/badge.svg)](https://github.com/Emiliamio/agent-forge/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/badge/Tests-35%20Passed%20(100%25)-success.svg)]()
[![License](https://img.shields.io/badge/License-Commercial%20%2F%20Enterprise-blue.svg)](COMMERCIAL_LICENSE.md)

[中文版文档 (Chinese)](README.md) | [English Documentation](README_EN.md) | [技术博客](https://emiliamio.github.io) | [商业授权 & SLA](COMMERCIAL_LICENSE.md)

> **AgentForge** 是一套完全基于 **Java 21（虚拟线程）+ Spring Boot 3.2 + PostgreSQL 16 (pgvector) + Vue 3.4** 构建的工业级商业 AI Agent 智能体编排与三路混合 RAG（检索增强生成）知识库中台。  
> 专为国内政企、国企信创生态、企业私有化交付量身定制，彻底摆脱 Python 框架在企业级环境中的运维困境。

---

## 🌟 核心技术壁垒与架构亮点

```
                                  AgentForge 核心架构全景图
   ┌─────────────────────────────────────────────────────────────────────────────────┐
   │                          用户接入层 (Multi-Channel Ingress)                      │
   │      Vue 3.4 管理中台   │   小白员工极简 Copilot 门户   │   Shadow DOM 嵌入挂件      │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                     安全防护与多租户物理隔离层 (Security & Isolation)             │
   │  • Sa-Token 统一身份认证与 RBAC 权限体系                                          │
   │  • MyBatis-Plus JsqlParser SQL AST 语法树租户强隔离 (跨租户物理越权率 0.00%)     │
   │  • 金融级 PII 双向可逆敏感脱敏 (手机号/身份证/银行卡) + DFA 毫秒级敏感词安全过滤   │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                    三路混合 RAG 深度检索层 (Hybrid RAG Pipeline)                 │
   │  • 密集向量检索 (pgvector HNSW) + 稀疏全文检索 (tsvector GIN)                    │
   │  • RRF (倒数排名融合算法) + Cross-Encoder 交叉重排模型二次评分                   │
   │  • 父子 Small-to-Big 双层分块 + 多轮对话 Query 智能指代消解重写                  │
   │  • 原文句子级精准高亮溯源与字符 Offset 双栏分屏对照                              │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                    响应式 DAG 工作流引擎 (Reactive Workflow Engine)              │
   │  • 基于 Kahn 拓扑排序算法的并发分层调度 (Project Reactor / Flux 并发流)          │
   │  • 9 大反应式节点执行器 (LLM、RAG、Code、HTTP、Switch、Human-in-Loop、Text2SQL等) │
   │  • ReAct Agent 智能体状态机 + 动态 OpenAPI 工具箱编排                            │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                   生产级装甲防御与成本优化 (Hardened Armor & Optimization)        │
   │  • Redis 向量语义降本缓存 (余弦相似度 >= 0.95 秒回，实测降低 60% 算力费)         │
   │  • 800MB 破损/加密文件装甲流式解析器 (零内存泄漏 + 逐页死信 DLQ 跳过)             │
   │  • 脱网老旧机 (Postgres 10/12) 纯 Java 内存向量余弦降级引擎                      │
   │  • 多模型级联熔断 (DeepSeek -> 局域网 Ollama -> OpenAI) + Single-Flight 防击穿   │
   │  • Zero-DBA 数据库自动建表灌数 + API Key 智能轮询欠费自动摘除池                  │
   └─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 极速 3 分钟一键部署

### 方式 1：Docker Compose 生产一键启动（推荐）
```bash
# 启动全栈生产容器 (Postgres 16 pgvector + Redis 7 + 后端 Java 21 + 前端 Nginx)
docker-compose -f docker-compose-prod.yml up -d
```
* 前端工作台：`http://localhost`
* 默认管理员：`admin` / `admin123456`（默认租户 ID: `1`）

### 方式 2：本地源码运行与调试
```bash
# 1. 启动底层存储
docker-compose up -d postgres redis

# 2. 启动 Spring Boot 后端 (内置 Zero-DBA 自动初始化数据库)
cd backend && mvn spring-boot:run

# 3. 启动前端 Studio
cd frontend && npm install && npm run dev
```

---

## 🧪 自动化测试验证

AgentForge 后端内置覆盖 AST 租户隔离、Kahn DAG 调度、RAG 混合检索、PII 脱敏、DFA 过滤等全套自动化测试：

```bash
cd backend && mvn clean test
```
* **单测通过率**：**35 / 35 全部通过 (100% 绿灯)**
