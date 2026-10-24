# 贡献指南

## 分支与提交

- `main` 受保护，仅接受通过 CI 的 PR 合并；
- 功能分支：`feature/<短描述>`，修复分支：`fix/<短描述>`；
- 提交信息遵循 Conventional Commits：`feat|fix|test|docs|ci|refactor|build|chore(scope): 摘要`。

## 提交前必跑

```bash
docker compose up -d postgres redis && bash scripts/prepare-live-db.sh
cd backend && mvn -B clean verify -Dlive=true
cd ../frontend && npm ci && npm run lint && npm test && npm run build
cd .. && python scripts/benchmark.py --out /tmp/latest.json \
        && python scripts/check_benchmark.py --baseline docs/benchmark/stable.json --current /tmp/latest.json
docker exec agentforge-redis redis-cli -a redis123 FLUSHALL   # 清限流残留，避免 429 假失败
python scripts/check_doc_claims.py && python scripts/check_doc_commands.py   # 文档宣称与命令引用门禁
```

## 工程质量纪律（强制，验收前置条件）

**完整版见 [`docs/ENGINEERING-DISCIPLINE.md`](docs/ENGINEERING-DISCIPLINE.md)，六条要点：**

1. **说"不可修复"必须带证据**：给 `npm view <pkg>@<ver> engines` 与 OSV `fixed` 区间的原始输出，
   不允许把 `npm audit` 的建议版本当作"最小可修版本"（v1.3.1 的 vitest 5 误判就是这么来的）。
2. **写进文档的每条命令必须自己先跑一遍**：`@ArchTest` 无法用 `-Dtest=类#方法` 精确选中，
   surefire 会静默退化成跑整类（v1.2.0 文档里的按方法名选规则就是假精确）。
3. **新增/修改门禁必须反向验证**：L1 注入违规 → 红 → 回滚 → 绿；L2 **每类载体**分别注入（围栏命令 / 行内代码 / 脚本引用 / npm task / 锚点 / 量化表述）；L3 核对门禁自报覆盖数。**安全类断言必须走真实攻击路径**（不加伪造头、从发布端口发、含路径变体），不许用只有你自己会发的请求来证明"外部不可达"。
4. **改依赖必须刷 SBOM 并收紧清单**：`bash scripts/refresh-sbom.sh`；
   已修复项要从 `osv-scanner.toml` / `.trivyignore` 删掉，新增豁免必须带 `reason` + `ignoreUntil`。
5. **宣称与文档门禁**：任何新增量化表述或性能宣称必须挂 `<!-- claim:N -->` 锚点或登记豁免；
   文档中所有命令引用与标题锚点必须能被机器解析执行（`python scripts/check_doc_claims.py && python scripts/check_doc_commands.py` 必须通过）。
6. **编码纪律**：所有文本文件 UTF-8 无 BOM、LF（BOM 曾一次连坐 4 个 CI job）。

附加：测试类必须自清全局静态状态（`TableInfoHelper` / `TenantContextHolder`），
不得依赖其它测试类的执行顺序。

## 代码要求

- 保留既有分层（controller → service → mapper），不新增跨层直连；
- 需要改分层或包结构时，先看 `docs/adr/0006-architecture-invariants.md` 的 R1–R6 不变量，
  并用 `mvn -B -Dtest=ArchitectureTest test` 验证（12 项全绿）；
- 新增/修改业务逻辑必须补测试，覆盖率不得低于门禁（BUNDLE ≥ 0.60、`com.agentforge.service.*` ≥ 0.70）；
- 对外 API 变更必须同步 `docs/openapi.json`，否则 `OpenApiContractTest` 会阻断；
- 禁止提交真实凭据；演示口令统一走 `.env.example` 与 `seed-data.sql`；
- 不得手改 `docs/benchmark/stable.json` 与 `docs/BENCHMARK.md` 的指标数字（由脚本生成）。

## 禁止项

1. `@Disabled` / 删断言 / 放宽 `--max-warnings` / 加大 sleep 让测试变绿；
2. `--no-verify` 跳过 gitleaks，或往 `.gitleaks.toml` 加白名单；
3. 无 `reason`、无 `ignoreUntil` 的依赖豁免；
4. 修改 `scripts/check_osv.py` 的判定逻辑以求构建通过；
5. 文档里写未实际执行的验证命令，或声称"已修复"但不贴证据。