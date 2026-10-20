# 贡献指南

## 分支与提交

- `main` 受保护，仅接受通过 CI 的 PR 合并；
- 功能分支：`feature/<短描述>`，修复分支：`fix/<短描述>`；
- 提交信息遵循 Conventional Commits：`feat|fix|test|docs|ci|refactor(scope): 摘要`。

## 提交前必跑

```bash
docker compose up -d postgres redis && bash scripts/prepare-live-db.sh
cd backend && mvn test -Dlive=true && mvn verify
cd ../frontend && npm run lint && npm test && npm run build
cd .. && python scripts/benchmark.py --out /tmp/latest.json \
        && python scripts/check_benchmark.py --baseline docs/benchmark/stable.json --current /tmp/latest.json
```

## 代码要求

- 保留既有分层（controller → service → mapper），不新增跨层直连；
- 新增/修改业务逻辑必须补测试，覆盖率不得低于门禁；
- 禁止提交真实凭据；演示口令统一走 `.env.example` 与 `seed-data.sql`；
- 不得手改 `docs/benchmark/stable.json` 与 `docs/BENCHMARK.md` 的指标数字（由脚本生成）。
