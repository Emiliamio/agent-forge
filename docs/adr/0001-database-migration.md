# ADR 0001: 数据库版本化迁移与 DDL 解耦方案

## 状态
已接受 (Accepted) - 2026-10-20

## 背景与上下文
AgentForge 早期采用自研 `DatabaseAutoInitializer` 在 Java 启动阶段解析并执行 `sql/schema.sql`，配合应用内 `schema_version` 表进行多轮补丁演进。该方案存在三大痛点：
1. **启动时 DDL 隐患**：应用进程持有 DDL 执行权限，在多实例横向扩展部署时存在竞态执行冲突；
2. **结构漂移风险**：历史结构与 Java 补丁逻辑交织，导致如 `api_key.expired_at` 未统一对齐时区字段类型；
3. **运维与审查割裂**：标准 DBA 无法通过业界通用工具链进行版本追溯、Dry-Run 审查与前置预执行。

## 决策内容
1. **引入行业标准 Flyway**：全面采用 Flyway 10.10.0 接管所有表结构与函数触发器的版本化 DDL；
2. **迁移脚本切分与基线对齐**：
   - `V1__baseline.sql`：13 张核心业务实体表、HNSW 余弦向量索引与基础 GIN 索引；
   - `V2__cjk_bigram_functions.sql`：CJK Bigram 分词函数与自动倒排索引触发器；
   - `V3__agent_public_columns.sql`：智能体安全挂件字段（`public_enabled`, `public_token`）；
   - `V4__chunk_tsv_backfill.sql`：全量历史切片 CJK 倒排索引重算回填；
   - `V5__time_column_alignment.sql`：彻底对齐历史遗留时区字段（`api_key.expired_at` 转为 `timestamp without time zone`）；
3. **存量环境平滑演进策略**：
   - 开启 `spring.flyway.baseline-on-migrate: true`，设定 `baseline-version: 1`；
   - 针对老数据库环境，自动以基线 V1 登记，并按需顺序执行 V2~V5；新库则自 V1 依次执行至 V5；
4. **彻底物理退役运行时 Java DDL**：
   - 删除 `DatabaseAutoInitializer` 与 `sql/schema.sql`；
   - 种子数据解耦为只读 `DataSeeder`（仅空库时注入）；
   - 新增 `SchemaHealthChecker` 进行只读启动状态与字段健康巡检。

## 影响评估
- **收益**：应用启动脱敏，彻底消除多实例 DDL 竞态；支持标准 CI/CD 与 DBA 迁移流水线；存量老库零中断升级。
- **代价**：团队必须严格通过 `V*__description.sql` 编写增量迁移脚本，禁止在任何 Java 代码中硬编码 DDL。
