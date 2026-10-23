# 📝 AgentForge 更新发布日志 (Changelog)

所有关于 AgentForge 项目的重要更新与版本迭代记录均归档于此文件。  
版本号遵循 [Semantic Versioning 2.0.0](https://semver.org/lang/zh-CN/) 语义化版本规范。

## [v1.3.1] - 2026-10-23

### Fixed
- **前端依赖漏洞清零（14 → 0）**：`vitest 3.2.6 → 4.1.12`（`@vitest/mocker` 随之 4.1.12，`tinypool` 依赖被移除，两条 9.5 分告警消失）；OSV 扫 `docs/sbom/frontend-bom.json` 输出 `No issues found`，trivy SBOM 扫描 `Clean`。
- **纠正 C 批的错误结论**：初版 VEX 断言"残留 5 条必须升 vitest 5、而 vitest 5 需要 Node ≥22.12，故不可修"——实测 `vitest@4.1.12` 的 engines 为 `^20 || ^22 || >=24`，Node 20 基线即可运行，无需升 Node、无需改 CI、无需动 SBOM 渲染容器（正文见 `docs/security/VEX.md §3.4`）。
- **安全清单收紧**：`osv-scanner.toml` 清空全部豁免条目；`.trivyignore` 从 20 条缩减到**后端 7 条**（Spring 7.0.9 / opennlp 2.5.9 / tomcat 10.1.58 未发布），前端条目全删。
- **前端工具链落地版本**：`vite 6.4.4`、`@vitejs/plugin-vue 5.2.4`、`tailwindcss 4.3.3`（`@tailwindcss/vite`，移除 `postcss`/`autoprefixer`）、`eslint-plugin-vue 10.11.1`（保持 ESLint 8.57）。

## [v1.3.0] - 2026-10-23

### Changed
- **Spring Boot 3.4.13 → 3.5.16**：清掉 `spring-boot` / `spring-boot-autoconfigure` / `spring-boot-starter-actuator` / `spring-data-commons` / `spring-data-keyvalue` 五条跨线 CVE；Maven 侧 OSV 告警 **14 条 → 8 条（4 个包）**。
- **MyBatis-Plus 3.5.5 → 3.5.17**：适配 Boot 3.5 的模块化拆分，新增 `mybatis-plus-spring` 与 `mybatis-plus-jsqlparser` 两个官方模块（`PaginationInnerInterceptor` / `TenantLineInnerInterceptor` / `IService` / `ServiceImpl` 所在）。
- **Sa-Token 1.37.0 → 1.46.0**：适配 Boot 3.5 与 Jakarta Servlet 上下文；相关 Mockito 测试改为显式保存/还原 `SaManager` 全局上下文（ThreadLocal 隔离）。
- **移除 Redisson**：`redisson-spring-boot-starter` 的 `spring-data-32` 适配与 Spring Data Redis 3.5.13 的 `pExpire` 存在二进制不兼容并触发递归调用（即 Boot 3.5 spike 中 `/auth/login` 的 `StackOverflowError` 真正根因）。本项目限流/锁/会话全部走 Spring Data Redis + Lua，无需 Redisson 客户端，移除后回归原生 Lettuce。SBOM 组件数 177 → 164。

### Fixed
- **Boot 3.5 spike 结论归档**：`StackOverflowError` 不是 Sa-Token 的问题，而是 Redisson × Spring Data Redis 3.5 的 `pExpire` 递归；该结论与试错组合已写入 `docs/security/VEX.md`，避免后人再走一遍。

## [v1.2.1] - 2026-10-23

### Changed
- **B2 特性域补全**：`service/{billing,security,platform}` 各切分 `api` / `internal` 两层，加上此前的 `agent`、`rag`，共 **5 个特性域 × 2 层**；`converter`（共享映射层）保持原位。
- **新增通用隔离规则 Rule 11**：`slices().matching("com.agentforge.service.(*).internal..").should().notDependOnEachOther()`，一次性覆盖所有特性对（不再逐对手写）。实测注入 `billing.internal → security.internal` 依赖时立即变红，回滚后转绿。
- **测试隔离回归、去掉每类独立 JVM**：`DocumentAndDatasetServiceTest` 与 `HexagonalPortsAndFakesTest` 在 `@BeforeEach`/`@AfterAll` 主动清理 `TableInfoHelper` 元数据与 `TenantContextHolder`，surefire 恢复默认共享 JVM。全量 `mvn clean verify -Dlive=true` 由 **123.1s → 63.8s**，且正序/逆序两种执行顺序均绿（216 用例）。

## [v1.2.0] - 2026-10-23

### Added
- **架构不变量 R1–R6 与机器门禁**：`ArchitectureTest` 扩到 **11 项**（10 条 ArchUnit 规则 + 1 个 DDL 扫描），覆盖严格单向分层（controller↛mapper、mapper↛service、entity↛service/mapper）、DTO/VO 边界、事务只允许在 service、包级切片无环（`slices().matching("com.agentforge.(*)..").should().beFreeOfCycles()`）、特性内部实现双向隔离（agent.internal ↔ rag.internal）；零白名单、零忽略注解。
- **控制器全面 DTO/VO 化**：15 个控制器中 10 个不再 `import com.agentforge.entity`（`grep` 结果为 0），出入参改为 `dto`/`vo` + 手写转换器，**对外字段名零变更**。
- **六边形端口与测试替身**：新增 `ChatModelPort` / `EmbeddingPort` / `DocumentStoragePort` 三个端口接口及对应 `Fake*` 实现，由 `HexagonalPortsAndFakesTest` 真实注入驱动。
- **OpenAPI 契约快照门禁（R5）**：`docs/openapi.json`（OpenAPI 3.1.0 / 47 paths / 91 schemas）+ `OpenApiContractTest` 规范化比对；实测注入一个临时端点即刻红，回滚后转绿。springdoc `2.3.0 → 2.8.5`。
- **架构决策记录**：`docs/ARCHITECTURE.md` 六大不变量章节（为什么 / 真实事故 / 反例 / 验证命令）+ `docs/adr/0006-architecture-invariants.md`（含实现现状与已知边界）。

### Fixed
- **测试顺序依赖（真实缺陷）**：`DocumentAndDatasetServiceTest` 在 `HexagonalPortsAndFakesTest` 之后运行时，因 MyBatis-Plus `TableInfoHelper` / `TenantContextHolder` 的全局静态状态被污染，`getById` 返回 null、断言退化为 `zero interactions`；surefire 改为 `reuseForks=false`（每个测试类独立 JVM）根除该类问题，全量 215 用例稳定绿（123s）。
- **文档与实现对齐**：修正 `ARCHITECTURE.md` 中"按方法名精确选择 ArchUnit 规则"的伪命令（`@ArchTest` 是静态字段，surefire 无法按方法过滤，会退化成跑整类）；补齐文档宣称但缺失的 R1 两条规则。
- **SBOM 与依赖同步**：springdoc 升版后重新生成 `docs/sbom/*.json`（后端 175 → 177 组件）。

## [v1.1.3] - 2026-10-23

### Security
- **容器镜像非 root 化 + 容器级健康检查**：后端运行用户 `10001:10001`（并 `chown` 应用目录），前端改用 `nginxinc/nginx-unprivileged:1.27-alpine`（uid 101，监听 8080）；两个 Dockerfile 均加 `HEALTHCHECK`。`trivy config` 由 4 条告警（2×root、2×无 HEALTHCHECK）清零；`docker-compose{,-prod}.yml` 前端端口映射改为 `3000:8080`、健康检查统一走 `127.0.0.1`。
- **Spring Boot 3.2.3 → 3.4.13 + 全量补丁级依赖钉版**：Tomcat `10.1.19→10.1.56`、Spring `6.1.4→6.2.19`、Jackson `2.15.4→2.18.11`、Logback `1.4.14→1.5.34`、Netty `4.1.107→4.1.137.Final`、Micrometer `1.12.3→1.15.12`、Log4j `2.24.3→2.25.5`、PostgreSQL `42.6.1→42.7.12`、commons-compress `1.26.0`、commons-lang3 `3.18.0`、POI `5.4.0`、opennlp `1.9.5`。Maven 侧 OSV 告警由 **145 条降到 14 条**，剩余项全部属于跨 minor/大版本线（见 `docs/security/VEX.md`）。
- **新增可审计依赖门禁 `scripts/check_osv.py`**：机器判定"是否落后于已发布补丁"——同 minor 线内存在已发布修复版本即构建失败；跨版本线或尚未发布的修复项打印清单并记录归档，无人工白名单。
- **trivy 改用 SBOM 扫描**：`trivy sbom docs/sbom/*.json` 不触发 Maven Central 依赖解析，彻底消除 429 限流导致的随机失败；另保留 `trivy fs --scanners misconfig,secret` 做配置与密钥检查。
- **前端 `follow-redirects` 固定到 1.16.1**（`overrides`），修复 GHSA-8r9p-f939-6h3c / GHSA-q3jh-wrc6-jgq8。

### Fixed
- **CI live 测试库建表**：`flyway:migrate` 在全新 checkout 上因 `classpath:db/migration` 无编译产物而扫到 0 条迁移、静默退出，导致种子脚本报 `relation "sys_tenant" does not exist`；改显式 `-Dflyway.locations=filesystem:src/main/resources/db/migration`。
- **SBOM 漂移门禁误报**：`normalize_sbom.py` 追加剔除 cyclonedx 的 `cdx:reproducible` 元数据属性（是否出现取决于构建环境，不属于依赖证据）。

## [v1.1.2] - 2026-10-23

### Fixed
- **CI 安全工具安装权限**：`security-audit` 中 gitleaks 解包与 OSV-Scanner 安装改为 `sudo tar` / `sudo install`，修复 runner 用户对 `/usr/local/bin` 无写权限导致 7 秒即失败的问题。
- **CI Redis 认证不匹配**：`backend-test` 与 `benchmark-drift` 不再使用无密码的 `services.redis`，改为 `docker run ... redis-server --requirepass redis123`，与 `LiveTestSupport` 的 `spring.data.redis.password=redis123` 对齐，修复 Redisson `Unable to connect to Redis server` 引发的全量 live 测试失败。
- **SBOM 跨平台漂移**：`scripts/refresh-sbom.sh` 的前端 BOM 改为固定用 `node:20` 容器渲染（npm 会按平台裁剪 `@esbuild/*`、`@rollup/rollup-*` 等 optionalDependencies，Windows 与 Linux 生成结果必然不同），并补充 `mvn`/`mvn.cmd` 探测；重新生成 `docs/sbom/frontend-bom.json`（linux/amd64 口径），使 CI 的 `sbom-drift` 可稳定通过。

## [v1.1.1] - 2026-10-22

### Security
- **生产编排强制环境变量口令门禁**：`docker-compose-prod.yml` 彻底移除弱默认密码，强制使用 `${POSTGRES_PASSWORD:?...}`、`${REDIS_PASSWORD:?...}` 与 `${JWT_SECRET:?...}`；新增生产环境密钥生成工具 `scripts/gen-env.sh`（自动生成 32 字节高强度随机十六进制密钥并设置 600 权限）与 `.env.example` 占位模板。
- **生产应用 JWT 密钥显式注入**：`backend/src/main/resources/application-prod.yml` 显式设置 `sa-token.jwt-secret-key: ${JWT_SECRET}`，杜绝内置默认密钥。

### Fixed
- **数据库备份生命周期自动保留策略**：`scripts/backup.sh` 引入 `BACKUP_RETAIN_DAYS`（默认 14 天），备份归档完成后使用 `find -mtime` 自动清理超期 `.dump` 与 `.meta.json` 快照，避免长期运行导致宿主机磁盘耗尽。
- **技术宣称校验门禁零污染执行**：`scripts/benchmark_external.py` 支持 `--out` 指定输出路径；`scripts/verify-claims.sh` 在临时文件中渲染外部评测报告并自动对比四大核心指标（Recall@1、Recall@5、MRR、NDCG@5），脚本末尾增加严格的工作区干净检查门禁，彻底消除 CI 与本地自验对 `docs/BENCHMARK-EXTERNAL.md` 的污染。

### Added
- **待验声明跟踪机制**：在交付手册与发布日志中明确记录 Claim 10（冷启动 ≤60s）依赖官方公开镜像仓库发版闭环；在无公网镜像推送环境下诚实记录 `[BLOCKED]` 状态，杜绝虚假打点。

## [v1.1.0] - 2026-10-22

### Added
- **可复现宣传指标矩阵与 CI 门禁 (`docs/CLAIMS.md`)**：建立包含 9 项关键技术宣称的机器可验证矩阵，配套全自动自验脚本 `scripts/verify-claims.sh` 与 GitHub Actions CI `claims-verify` 门禁。
- **两阶段挂件密钥安全轮换与 Flyway V6 迁移**：数据库新增 `public_token_prev` 字段，支持平滑密钥轮换与回滚，配套两阶段轮换 CLI 工具 `scripts/rotate_widget_secret.py` 及安全执行手册。
- **挂件安全威胁建模规范 (`docs/THREAT_MODEL-widget.md`)**：建立遵循 STRIDE 模型的挂件安全防护威胁分析矩阵，全面覆盖 6 类威胁缓解策略与残余风险应对。
- **预构建镜像发布与离线打包支持**：CI 自动构建并发布前后端镜像至 GHCR；`docker-compose.yml` 引入预构建镜像及本地构建回退；新增 `scripts/export-images.sh` 导出全栈离线镜像包（含 SHA256SUMS 与 README-offline.txt）。
- **主库破坏性灾难恢复演练 (`scripts/restore-to-primary.sh`)**：支持主动断开长连接、物理销毁并从最新备份归档恢复主库并执行全套冒烟回归，集成入 `.github/workflows/drill.yml`。

### Changed
- **控制器与持久层依赖彻底解耦（零例外分层契约）**：
  - 抽取并拓展 5 大 Service 门面（`AgentAppService`、`AuthQueryService`、`BillingService`、`ChatSessionService`、`WorkflowService`）；
  - 全面移除 6 大 Controller（`AgentAppController`、`AuthController`、`BillingController`、`ChatSessionController`、`PublicAgentController`、`WorkflowController`）对 Mapper 的所有直接依赖；
  - 收紧 `ArchitectureTest` 规则至零白名单、零豁免；归档并偿还 ADR 0004，发布 ADR 0005。
- **外部语料评测 Commit SHA 自动对齐**：`scripts/benchmark_external.py` 生成的评测报告中 Git Commit SHA 动态绑定生成时的当前 HEAD。
- **快速拉起路径优化**：README 明确区分官方预构建镜像极速拉起（≤60s）与源码编译路径（3~5分钟）。

### Security
- **明文挂件令牌安全软下线开关**：引入 `agentforge.widget.legacy-token-enabled` 配置项（默认开启明文兼容并输出 2027-01-01 Sunset 警示日志；生产可随时关闭强制收敛至 v1 签名令牌）。
- **Redis 故障时安全策略显式化**：引入 `agentforge.security.fail-open-when-redis-down` 配置（开发默认 true 优雅降级；生产默认 false 拒绝放行并返回 503 与 Retry-After: 5）。
- **Nginx 工业级安全响应头与挂件跨域白名单**：前端 Nginx 默认注入 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer`、`Permissions-Policy` 等安全头，针对 `/agentforge-widget.js` 严格保留单文件跨域白名单（`Access-Control-Allow-Origin: *`）。

### Fixed
- **生产环境 Compose 端口与健康校验对齐**：`docker-compose-prod.yml` 增加 `${FRONTEND_PORT:-3000}:80` 端口映射，镜像名与主编排统一。

## [v1.0.0] - 2026-10-22

### Added
- **Flyway 数据库版本化迁移矩阵**：移除全部 Java 运行时 DDL，数据库表结构与索引演进全面收敛至 Flyway `V1__baseline.sql` ~ `V5__time_column_alignment.sql` 迁移脚本。
- **一键全栈交付编排与冒烟自检**：提供 `docker compose --profile full up -d --build --wait` 一键全栈交付，新增前后端 `.dockerignore` 镜像构建瘦身与 12 步自动化冒烟脚本 `scripts/smoke.sh`。
- **备份恢复双向闭环与定时演练**：新增 `scripts/backup.sh` 与支持 7 项活体校验的 `scripts/restore.sh --verify`，配置 GitHub Actions 定时演练工作流 `.github/workflows/drill.yml`。
- **v1 签名挂件令牌与防重放守护器**：引入 `v1.<exp>.<nonce>.<hmacBase64Url>` 签名规范与生成工具 `scripts/sign_widget_token.py`，实现支持 30s 时钟漂移宽限与 Redis/LRU 双通道防重放守护器 `ReplayGuard`。
- **原子多维限流与动态 Retry-After**：实现基于 Redis 原子 Lua 脚本的 `LuaRateLimiter`，统一串联 IP (30 RPM)、Agent (300 RPM) 与 Token (60 RPM) 三维防御，支持动态计算 `Retry-After: 1..60s` 响应头，提供并发压测探针 `scripts/limit_probe.py`。
- **第三方公开权威语料评测**：基于公有领域《民法典》总则 45 法条切片构建真实中立评测基准 `docs/benchmark_external_corpus.md`、`scripts/benchmark_external.py` 与客观评测报告 `docs/BENCHMARK-EXTERNAL.md`（Recall@1 81.25%、Recall@5 93.75%、MRR 0.8777、NDCG@5 0.8914）。
- **生产环境强化配置与编排健壮性**：提供 `backend/src/main/resources/application-prod.yml` 与 `docker-compose-prod.yml`，严格禁用演示种子、隐藏敏感堆栈、前后端注入容器级 healthcheck 与编排依赖。

### Changed
- **检索参数配置化解耦**：稠密与稀疏权重、TopK 与候选切片召回深度全面收敛至 `agentforge.rag.*` 属性树，官方基准评测持续锁定为 Recall@5 100%、MRR 0.9722、NDCG@5 0.9795。
- **DDL 彻底从运行时移除**：完全移除 Java 动态建表与初始化逻辑，表结构演进严格由 Flyway 托管。
- **访问入口规范收敛**：访问入口收敛至前端工作台 `http://localhost:3000`（Nginx 托管 + 反代 `/api` 到后端），后端 API 独立托管于 `http://localhost:8080/api/*`，根路径清晰返回 404。

### Fixed
- **验签顺序严谨性重构**：服务端严格先执行 HMAC-SHA256 常量时间签名校验，验签通过后才记录 Nonce，杜绝未签名伪造请求恶意烧掉合法请求的 Nonce 防重放窗口。
- **演练脚本 jar 包通配解析**：`scripts/upgrade-drill-flyway.sh` 采用通配符解析版本号步进后的目标 jar 包（`agent-forge-backend-1.0.0.jar`），杜绝旧快照版本写死导致的执行失败。
- **容器健康检查回环地址统一**：前后端容器 healthcheck 回环探测统一锁定 `127.0.0.1`，Nginx 开启 IPv6 双栈监听 (`[::]:80`)，彻底杜绝回环探测超时问题。
- **数据结构时区漂移修复**：彻底修复 `api_key.expired_at` 历史遗留的 `TIMESTAMP WITH TIME ZONE` 类型不一致问题。
- **降级检索向量污染隔离**：修复无外部 Embedding API Key 降级模式下伪稠密向量对 RRF 重排的排序干扰。
- **评测防篡改门禁校验强化**：完善 `check_benchmark_doc.py` 对评测报告 Markdown 正文表格与机器基线的一致性全量校验。

### Security
- **签名令牌强绑定 Agent 与 Tenant**：签名内容严格绑定 `agentId + "|" + tenantId + "|" + exp + "|" + nonce`，过期防护与防重放双重守护，拦截跨 Agent/租户伪造。
- **原子多维速率防护与防时序攻击**：挂件密钥比对采用 `MessageDigest.isEqual` 恒定时间防时序攻击；多维原子限流配合动态 `Retry-After: 1..60s` 响应头防御突发流量。
- **16 场景全维越权矩阵测试覆盖**：`AuthzMatrixTest` 完整覆盖 401 匿名/伪造拦截、403 越权/禁用拦截、404 挂件不存在拦截、429 超频限流与动态 `Retry-After` 断言。
- **CI 安全扫描与代码健康门禁**：集成 Gitleaks 密钥泄露扫描、OSV 依赖供应链漏洞审计、Trivy 容器漏洞扫描与 SpotBugs 静态安全分析。

---

## 🚀 [v1.2.0] - 2026-10-15 (Enterprise Apex Full Perfection Edition)

### 🌟 新增 Apex 核心中枢与交互升级 (Apex Features & Interactive Studio)
* **工业级 SVG 贝塞尔曲线交互式 DAG 工作流画布 (`/workflows`)**：
  * 支持 2D 自由节点布局、贝塞尔连线曲率自动计算与带箭头流向指示；
  * 支持 Start、LLM、RAG、Code 沙箱、Condition 条件分支等多路并发拓扑调度；
  * 内置 **APM 全链路时序甘特图 (Trace Waterfall Gantt)** 视图，直观展示各 Span 绝对偏移、相对耗时与层级深度。
* **DeepSeek-R1 风格结构化思考过程与思辨链折叠 (`/copilot`)**：
  * 新增可折叠展开的 `🧠 已深度思考 (用时 1.4 秒)` 思考盒；
  * 思维脉络与正文回答分流，结合 GraphRAG 两跳知识图谱规章引用。
* **EnterpriseApexController 统一能力中枢 API**：
  * 对外完整暴露 GraphRAG 实体抽取/多跳扩散、代码安全沙箱、模型灰度竞技场、Anthropic MCP 原生协议工具调度及 Prompt 注入防护端点。
* **全套单测扩展至 52+ 项全部绿灯**：
  * 后端核心测试用例从 46 项扩充至 **52 项 100% 绿灯全部通过** (`BUILD SUCCESS`)。

---

## 🚀 [v1.1.0] - 2026-08-30 (Commercial Enterprise Edition)

### 🌟 新增功能与体验升级 (Features & UX)
* **全员 Copilot 极简门户 (`/copilot`)**：
  * 新增专为企业非技术员工、业务领导设计的免培训智能工作台；
  * 内置制度严谨、深度思考 (DeepSeek-R1) 与创意文案三大模式；
  * 支持差旅报销测算、合同违规自检、销售业绩统计与 Word (.docx) 一键公文排版导出；
  * 前端主导航栏与控制台首页新增直达高亮入口。
* **Web Component 嵌入式智能体挂件集成沙盒**：
  * 控制台首页新增嵌入式挂件专属体验面板；
  * 支持一键复制 `<script src="/agentforge-widget.js">` 两行代码；
  * 支持现场实时点击并在页面右下角动态注入 Shadow DOM 隔离挂件。

### 🏛️ 信创与政企交付资质 (Xinchuang & Compliance)
* **信创国产化软硬件适配矩阵**：
  * 新增 `docs/XINCHUANG_COMPATIBILITY_MATRIX.md` 白皮书，全面覆盖鲲鹏/飞腾/海光芯片、银河麒麟/统信 UOS 操作系统、人大金仓/达梦数据库及东方通/金蝶天燕中间件；
  * 新增 `docs/BIDDING_DEFENSE_FAQ.md` 招投标技术答辩 20 问专家攻防宝典；
  * 新增 `docs/DISASTER_RECOVERY_SOP.md` 企业级高可用容灾与秒级 RTO/RPO 灾备预案。

### 🛠️ 自动化运维与交付工具库 (DevOps & Scripts)
* 新增 `scripts/health_check.sh`：Linux/信创环境下 1 秒自动化全链路排查 Java 21 后端、PostgreSQL 16 向量扩展、Redis 延迟及磁盘 IO。
* 新增 `scripts/health_check.bat`：Windows Server 环境下一键双击自检脚本。
* 新增 `scripts/backup_database.sh`：PostgreSQL 向量数据与 Redis 语义缓存热备份与 7 天旧备份自动清理 SOP。

### 🧪 工程与测试质量 (Quality Assurance)
* 前端 Vite 生产构建速度优化至 **2.39 秒**，0 错误 0 警告；
* 后端全量 **35 项** 核心测试用例 100% 绿灯全部通过 (`BUILD SUCCESS`)。

---

## 📦 [v1.0.0] - 2026-08-28 (Initial Commercial Release)

* 纯血 Java 21 LTS 虚拟线程 + Spring Boot 3.2 核心底座；
* 基于 MyBatis-Plus 的 JsqlParser SQL AST 语法树层级租户强隔离；
* PostgreSQL 16 pgvector HNSW 稠密检索 + tsvector BM25 稀疏检索 + RRF 倒数排名融合 + Cross-Encoder 重排；
* 基于 Kahn 拓扑排序算法的响应式 DAG 工作流引擎 (Project Reactor)；
* Redis 向量余弦语义缓存 (节省 60%+ 大模型 API 算力成本)；
* 800MB 破损文件磁盘流式缓冲解析与长尾装甲自愈体系。
