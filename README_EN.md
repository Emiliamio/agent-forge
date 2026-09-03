# 🚀 AgentForge —— Pure Java 21 Enterprise AI Agent Platform & 3-Way Hybrid RAG Engine

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg?style=flat&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.4-emerald.svg?style=flat&logo=vuedotjs)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-blue.svg?style=flat&logo=postgresql)](https://github.com/pgvector/pgvector)
[![Redis](https://img.shields.io/badge/Redis-7.2-red.svg?style=flat&logo=redis)](https://redis.io/)
[![CI/CD Pipeline](https://github.com/Emiliamio/agent-forge/actions/workflows/ci.yml/badge.svg)](https://github.com/Emiliamio/agent-forge/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/badge/Tests-44%20Passed%20(100%25)-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-MIT%20%7C%20Commercial-blue.svg)](COMMERCIAL_LICENSE.md)

[中文版文档 (Chinese)](README.md) | [English Documentation](README_EN.md) | [Technical Blog](https://emiliamio.github.io) | [Commercial SLA & License](COMMERCIAL_LICENSE.md)

---

> **AgentForge** is an industrial-grade AI Agent orchestration and 3-Way Hybrid RAG (Retrieval-Augmented Generation) knowledge base platform built entirely with **Java 21 (Virtual Threads) + Spring Boot 3.2 + PostgreSQL 16 (pgvector) + Vue 3.4**.  
> Engineered specifically for financial institutions, state-owned enterprises, government Xinchuang ecosystems, and private on-premises delivery — completely overcoming the operational bottlenecks of Python frameworks in enterprise Java production environments.

---

## 🌟 Core Architectural Highlights & Technical Moat

```
                                  AgentForge Architecture Overview
   ┌─────────────────────────────────────────────────────────────────────────────────┐
   │                          Multi-Channel Ingress Layer                             │
   │      Vue 3.4 Admin Studio   │   Lightweight Employee Portal   │   Shadow DOM Widget │
   │  • DeepSeek-R1 Structured SSE Event Stream (Reasoning <think> separation + UI)   │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                     Security & Multi-Tenant Physical Isolation                   │
   │  • Sa-Token unified identity authentication & fine-grained RBAC                 │
   │  • MyBatis-Plus JsqlParser SQL AST physical tenant isolation (0.00% leakage)    │
   │  • Multi-tenant Token Budget & RPM Rate Limiter (TenantTokenQuotaLimiter)       │
   │  • Adversarial Prompt Injection & Jailbreak Guardrails (PromptInjectionGuard)   │
   │  • RAG Factuality & Grounding Guardrail (RagGroundingEvaluator score & warning) │
   │  • Financial-grade PII 2-way reversible masking + DFA millisecond safety filter │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                    3-Way Hybrid RAG Deep Retrieval Pipeline                      │
   │  • GraphRAG S-P-O entity triplet extraction & 2-hop topological traversal search │
   │  • Dense Vector Search (pgvector HNSW) + Sparse Full-Text (tsvector GIN)        │
   │  • RRF (Reciprocal Rank Fusion) + Cross-Encoder re-ranking secondary scoring    │
   │  • Parent-Child Small-to-Big 2-tier chunking + Multi-turn Query rewriting       │
   │  • Sentence-level exact provenance highlighting with character offset split-view │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                    Reactive DAG Workflow Engine                                  │
   │  • Kahn topological sorting concurrent execution (Project Reactor / Flux)       │
   │  • Secure Code Sandbox Engine (SecureCodeSandboxEngine with AST risk blocking)   │
   │  • Model Arena Canary Traffic Splitter (ModelArenaTrafficSplitter & benchmark)   │
   │  • LangSmith-grade Agent Execution Topology Waterfall & Token Cost Accounting   │
   │  • Anthropic MCP (Model Context Protocol) Native Client (JSON-RPC 2.0 ecosystem)│
   │  • 9 Reactive Node Executors (LLM, RAG, Code, HTTP, Switch, Human-in-Loop, SQL) │
   │  • ReAct Agent state machine + dynamic OpenAPI toolbox orchestration            │
   └──────────────────────────────────────┬──────────────────────────────────────────┘
                                          │
   ┌──────────────────────────────────────▼──────────────────────────────────────────┐
   │                   Hardened Production Armor & Cost Optimization                  │
   │  • Redis semantic vector cache (cosine similarity >= 0.95, 60% token cost cut)  │
   │  • 800MB damaged/encrypted document stream armor parser (Zero-OOM, page DLQ)   │
   │  • Pure Java in-memory vector cosine fallback engine for legacy off-grid DBs     │
   │  • Multi-LLM cascade failover (DeepSeek -> Local Ollama -> OpenAI) + SingleFlight│
   │  • Zero-DBA schema auto-initialization + API Key pool with quota auto-eviction  │
   └─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 3-Minute Quick Start

### Method 1: Docker Compose Production Launch (Recommended)
```bash
# Launch full-stack production containers (PostgreSQL 16 pgvector + Redis 7 + Java 21 Backend + Frontend Nginx)
docker-compose -f docker-compose-prod.yml up -d
```
* **Frontend Web Console**: `http://localhost`
* **Default Admin**: `admin` / `admin123456` (Default Tenant ID: `1`)

### Method 2: Local Source Code Development
```bash
# 1. Start underlying storage
docker-compose up -d postgres redis

# 2. Start Spring Boot Backend (Built-in Zero-DBA schema auto-init)
cd backend && mvn spring-boot:run

# 3. Start Frontend Studio
cd frontend && npm install && npm run dev
```

---

## 🧪 Automated Testing

AgentForge incorporates comprehensive regression testing covering AST tenant isolation, Kahn DAG reactive execution, parent-child chunking, RAGAS evaluations, and DFA content moderation:

```bash
cd backend && mvn clean test
```
* **Test Pass Rate**: **44 / 44 passed (100% Green)**
