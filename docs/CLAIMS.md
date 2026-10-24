# AgentForge 核心指标声明与机器复现矩阵 (Claims Matrix)

本文档对 AgentForge 在 README、评测报告及宣传材料中承诺的核心指标进行逐条机器复现锁定。所有声明均配有确定性复现命令与自动化验证脚本，杜绝任何人工手写或不可复现的数字宣传。

---

## 0. 声明口径与准入规范 (Scope Statement)

为确保项目所有对外技术表述具有 100% 机器可验的可信度，特此确立以下声明口径与准入规约：

1. **什么是 Claim（声明项）**：
   凡是对外发布的**可量化或可绝对化表述**，包括但不限于百分比（如 `100%`、`93.75%`）、耗时（如 `≤60s`）、数量/条数（如 `12 项`、`177+ 例`）、并发数与速率限制（如 `30 RPM`、`40 并发`）、绝对化承诺（如"零漏洞"、"全部可验"），均属于必须纳管的 Claim。
2. **新增 Claim 的三步闭环**：
   - **第一步：编写确定性断言**：在本地编写可脱机或机器可判的确定性验证断言（拒绝依赖外网偶发网络）；
   - **第二步：接入集中门禁脚本**：在 `scripts/verify-claims.sh` 中增加独立校验分支与清晰失败提示，并计入 `TOTAL_CLAIMS`；
   - **第三步：文档显式标记**：在公开文档对应的表述段落标注 `<!-- claim:N -->` 锚点或提供直达本矩阵的超链接。
3. **Claim 废弃与变更红线**：
   已登记的 Claim 严禁直接无故删行或静默放宽指标。若因架构演进而必须废弃或替代，必须在变更记录与本矩阵中显式声明作废原因、历史记录及承接的新 Claim 编号。

---

## 1. 核心技术指标与声明映射表

| 序号 | 声明指标项 | 宣称数值 / 期望现象 | 机器确定性复现命令 | 判定通过标准 (DoD) | 证据文件/产物 | CI job | 最近验证 |
| :---: | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **01** <a id="claim-01"></a><a id="claim-1"></a> | **内部检索准确率 Recall@5** | `100.0%` | `python scripts/benchmark.py --out /tmp/c.json && python scripts/check_benchmark.py --baseline docs/benchmark/stable.json --current /tmp/c.json` | 判定输出含 `[OK] No drift detected` | `docs/benchmark/stable.json` | `rag-benchmark` / `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **02** <a id="claim-02"></a><a id="claim-2"></a> | **内部检索排序指标 MRR / NDCG@5** | `MRR = 0.9722` / `NDCG@5 = 0.9795` | 读取 `docs/benchmark/stable.json` 的 `stable` 基线段 | `mrr == 0.9722` 且 `ndcg5 == 0.9795` 数值完全一致 | `docs/benchmark/stable.json` | `rag-benchmark` / `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **03** <a id="claim-03"></a><a id="claim-3"></a> | **第三方民法典权威语料检索 Recall@5** | `93.75%` (15/16 命中) | `python scripts/benchmark_external.py --deterministic` | 输出含 `Recall@5=93.75%` 且 `docs/BENCHMARK-EXTERNAL.md` 中的 `Git Commit SHA` 与当前 `HEAD` 一致 | `docs/BENCHMARK-EXTERNAL.md` | `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **04** <a id="claim-04"></a><a id="claim-4"></a> | **端到端冒烟全链路健康** | 12 项场景 100% 通过 | `bash scripts/smoke.sh http://localhost:8080 http://localhost:3000` | 终端输出包含 `[OK] All 12 smoke tests passed successfully!` | `scripts/smoke.sh` | `fullstack-smoke` / `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **05** <a id="claim-05"></a><a id="claim-5"></a> | **公开挂件单 IP 高并发限流** | 单 IP 30 RPM 且原子拦截 | `python scripts/limit_probe.py --url http://localhost:8080/api/public/agents/1/chat --concurrency 40` | 并发 40 请求结果严格分布为 `200=30 / 429=10 / 其他=0` | `scripts/limit_probe.py` | `rate-limit-drill` / `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **06** <a id="claim-06"></a><a id="claim-6"></a> | **后端自动化测试套件** | 224 用例、0 失败、0 跳过（复现需本地 PostgreSQL + Redis，否则 2 项 live 测试报错） | `cd backend && mvn -B clean verify -Dlive=true` | 终端输出 `Failures: 0, Errors: 0, Skipped: 0` 与 `BUILD SUCCESS` | `backend/target/surefire-reports/` | `backend-test` / `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **07** <a id="claim-07"></a><a id="claim-7"></a> | **JaCoCo 全局分支与指令覆盖率** | BUNDLE Coverage ≥ 0.60 | `cd backend && mvn -B clean verify -Dlive=true` | 输出含 `All coverage checks have been met.` | `backend/target/site/jacoco/index.html` | `backend-test` / `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **08** <a id="claim-08"></a><a id="claim-8"></a> | **数据库备份与临时库全量恢复** | 往返保真度 100% | `bash scripts/backup.sh && bash scripts/restore.sh --verify <latest_dump>` | 7 项表结构与业务数据校验全通过，输出 `round-trip restore verification PASSED` | `backups/*.dump`, `scripts/restore.sh` | `backup-drill` / `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **09** <a id="claim-09"></a><a id="claim-9"></a> | **一键全栈生产编排冷启动（≤60s）** | 镜像已就绪时 `up -d --wait` ≤ 60s 且四容器全 `healthy`；镜像可从 GHCR 匿名拉取 | `docker compose --profile full down && time docker compose --profile full up -d --wait` | 实测耗时 ≤ 60s（本机多次：6.6s / 17.8s / 23s），`backend`/`frontend`/`postgres`/`redis` 均为 `healthy` | `docker-compose.yml`, `scripts/verify-claims.sh` | `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **10** <a id="claim-10"></a> | **架构不变量 R1–R6 全部机器可验** | 12 项规则全绿、0 失败（11 条 ArchUnit 规则 + 1 个 DDL 领域约束扫描） | `cd backend && mvn -B -Dtest=ArchitectureTest test` | 终端输出 `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` 与 `BUILD SUCCESS` | `backend/src/test/java/com/agentforge/ArchitectureTest.java` | `backend-test` / `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **11** <a id="claim-11"></a> | **依赖漏洞口径严格可审计** | 前端 0 条漏洞与豁免；后端仅 7 条跨大版本/上游未发布项 | 离线断言：`osv-scanner.toml` 中 `^id = ` 计数为 0；`.trivyignore` 计数为 7；`frontend-bom.json` 中 `vite` ≥ 6.4.4、`vitest` ≥ 4.1.12 且无 `tinypool` | 三项离线断言全部通过，且 `python scripts/check_osv.py` 退出码为 0 | `osv-scanner.toml`, `.trivyignore`, `docs/sbom/frontend-bom.json` | `security-audit` / `claims-verify` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **12** <a id="claim-12"></a> | **交付镜像非 root 且带健康检查** | 生产镜像禁止 root 运行，且容器自带周期性主动健康探针 | 静态检查：`backend/Dockerfile` 含 `USER 10001:10001` 与 `HEALTHCHECK`；`frontend/Dockerfile` 含 `USER 101:101` 与 `HEALTHCHECK` | 两份 Dockerfile 均严格通过无特权用户与 HEALTHCHECK 指令校验 | `backend/Dockerfile`, `frontend/Dockerfile` | `claims-verify` / `publish-images` | `v1.3.1 @ 6f83f8a · 2026-10-23 · PASS` |
| **13** <a id="claim-13"></a> | **公开挂件调用全量留痕且可核验** | 一次成功调用产生 1 条 `outcome=ok` 记录；一次限流调用产生 `outcome=rate_limited` 且 `http_status=429` | `bash scripts/audit_probe.sh` | 两条记录字段齐全、`ip_hash`/`ua_hash` 均为 64 位十六进制、`latency_ms ≥ 0` | `widget_call_audit` 表 | `claims-verify` | 待验证 |
| **14** <a id="claim-14"></a> | **管理端口隔离：指标就绪且宿主端口无 actuator** | 管理端口 9090（容器内网）`/actuator/prometheus` 含 `http_server_requests_seconds_count`、`/actuator/health` 为 `UP`；宿主发布端口 8080 上 `/actuator/*` 与 `/api/actuator/*` 全部 404；`docker port` 中不含 9090 | `bash scripts/metrics_probe.sh` | 四组断言全部成立 | `scripts/metrics_probe.sh` | `claims-verify` | 待验证 |

---

## 2. 一键自动化全量声明验证

本地与 CI 门禁可通过以下命令执行全量自动化巡检：

```bash
bash scripts/verify-claims.sh
```

- 该脚本将逐条执行上述 14 个声明项，对输出与退出码进行强断言；
- 运行过程中自动检索最新备份快照、在执行限流测试前精准清理 `widget:*` 探针缓存、并在完成后自愈还原 Agent 1 原始配置状态；
- 任一指标未达成或产生漂移，脚本将立即退出并返回非 0 状态码 (`exit 1`)。

---

## 3. 官方镜像可拉取性（Claim 09 的前置条件）

CI 的 `publish-images` job 在 `main` 与 `v*` 标签上都会向 GHCR 推送镜像；仓库公开时可匿名拉取，
私有仓库需 `docker login ghcr.io`。本地实测（2026-10-23，`main` 分支构建）：

```
docker pull ghcr.io/emiliamio/agentforge-backend:latest    # sha256:f572a57df3e94f50c1ff3e525267bf7e5047987b42f5cf5d7f13f7ce218612f6
docker pull ghcr.io/emiliamio/agentforge-frontend:latest   # sha256:3030f634018272fb90e008913f6c12f1d9b85541b647e885ce1bc0d98ebaea05

docker compose --profile full pull      # 冷缓存 18.4s ~ 80.3s（随网络波动，不计入 60s 阈值）
docker compose --profile full up -d --wait  # 6.6s / 17.8s（计入阈值）
```

因此 Claim 09 的 60s 阈值口径为「**镜像已就绪时的启动耗时**」；拉取耗时受网络影响，单列观测值不参与判定。
