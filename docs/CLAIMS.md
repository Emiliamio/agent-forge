# AgentForge 核心指标声明与机器复现矩阵 (Claims Matrix)

本文档对 AgentForge 在 README、评测报告及宣传材料中承诺的核心指标进行逐条机器复现锁定。所有声明均配有确定性复现命令与自动化验证脚本，杜绝任何人工手写或不可复现的数字宣传。

---

## 1. 核心技术指标与声明映射表

| 序号 | 声明指标项 | 宣称数值 / 期望现象 | 机器确定性复现命令 | 判定通过标准 (DoD) |
| :---: | :--- | :--- | :--- | :--- |
| **01** | **内部检索准确率 Recall@5** | `100.0%` | `python scripts/benchmark.py --out /tmp/c.json && python scripts/check_benchmark.py --baseline docs/benchmark/stable.json --current /tmp/c.json` | 判定输出含 `[OK] No drift detected` |
| **02** | **内部检索排序指标 MRR / NDCG@5** | `MRR = 0.9722` / `NDCG@5 = 0.9795` | 读取 `docs/benchmark/stable.json` 的 `stable` 基线段 | `mrr == 0.9722` 且 `ndcg5 == 0.9795` 数值完全一致 |
| **03** | **第三方民法典权威语料检索 Recall@5** | `93.75%` (15/16 命中) | `python scripts/benchmark_external.py --deterministic` | 输出含 `Recall@5=93.75%` 且 `docs/BENCHMARK-EXTERNAL.md` 中的 `Git Commit SHA` 与当前 `HEAD` 一致 |
| **04** | **端到端冒烟全链路健康** | 12 项场景 100% 通过 | `bash scripts/smoke.sh http://localhost:8080 http://localhost:3000` | 终端输出包含 `[OK] All 12 smoke tests passed successfully!` |
| **05** | **公开挂件单 IP 高并发限流** | 单 IP 30 RPM 且原子拦截 | `python scripts/limit_probe.py --url http://localhost:8080/api/public/agents/1/chat --concurrency 40` | 并发 40 请求结果严格分布为 `200=30 / 429=10 / 其他=0` |
| **06** | **后端自动化测试套件** | 177+ 用例全绿、0 失败、0 跳过 | `cd backend && mvn -B clean verify -Dlive=true` | 终端输出 `Failures: 0, Errors: 0, Skipped: 0` 与 `BUILD SUCCESS` |
| **07** | **JaCoCo 全局分支与指令覆盖率** | BUNDLE Coverage ≥ 0.60 | `cd backend && mvn -B clean verify -Dlive=true` | 输出含 `All coverage checks have been met.` |
| **08** | **数据库备份与临时库全量恢复** | 往返保真度 100% | `bash scripts/backup.sh && bash scripts/restore.sh --verify <latest_dump>` | 7 项表结构与业务数据校验全通过，输出 `round-trip restore verification PASSED` |
| **09** | **一键全栈生产编排冷启动（≤60s）** | 镜像已就绪时 `up -d --wait` ≤ 60s 且四容器全 `healthy`；镜像可从 GHCR 匿名拉取 | `docker compose --profile full down && time docker compose --profile full up -d --wait` | 实测耗时 ≤ 60s（本机多次：6.6s / 17.8s），`backend`/`frontend`/`postgres`/`redis` 均为 `healthy`，`/api/system/health`=200、前端=200 |

---

## 2. 一键自动化全量声明验证

本地与 CI 门禁可通过以下命令执行全量自动化巡检：

```bash
bash scripts/verify-claims.sh
```

- 该脚本将逐条执行上述 9 个声明项，对输出与退出码进行强断言；
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
