# 依赖漏洞处置记录（VEX-lite）

本文件记录依赖漏洞门禁的**处置口径**与**不可在线内修复项**清单。

- 门禁脚本：`scripts/check_osv.py`（CI 的 `security-audit` job 调用，权威判定）
- 第二道扫描：`trivy sbom`（直接扫已提交 SBOM，不触发 Maven Central 解析 → 避免 429 限流导致的假失败）
- 机器清单：`osv-scanner.toml`（前端构建期依赖豁免）、`.trivyignore`（GHSA + CVE 双份同源清单）

## 0. 门禁判定规则（机器可判，无人工白名单）

对 `osv-scanner` 报出的每一条漏洞：

1. 向 OSV 查询该包**最小的、可用的修复版本**；
2. 若该修复版本落在**同一 minor 线**（如 `10.1.52 → 10.1.58`、`4.1.130 → 4.1.137`）且**已发布**到 Maven Central / npm
   → 判定为「落后于发布」，**构建立即失败**；
3. 若修复版本跨 minor/大版本线（如 Boot `3.4 → 3.5`、Spring `6.2 → 7.0`、opennlp `1.9 → 2.x`），或修复版本尚未发布
   → 判定为「需计划升级」，打印清单并放行，记入本文件第 2 节。

这条规则的意义：**补丁级的落后永远会红**（这是我们自己的责任），而跨版本线的升级必须作为独立批次做，不能靠白名单蒙混。

## 1. 已完成的修复（v1.1.3 & v1.3.0）

| 组件 | 原版本 | 处置 |
|---|---|---|
| Spring Boot | 3.2.3 → 3.4.13 | → **3.5.16**（升级至 3.5 minor 线，清空全部 Boot/Data 相关 CVE） |
| MyBatis-Plus | 3.5.5 | → **3.5.17**（适配 Spring 6.2+ / Boot 3.5 模块化重构） |
| Sa-Token | 1.37.0 | → **1.46.0**（全面兼容 Boot 3.5，优化 Jakarta Servlet 上下文隔离） |
| spring-data-commons / keyvalue | 3.4.13 | → **3.5.13**（随 Boot 3.5 官方 BOM 升级） |
| redisson-spring-boot-starter | 3.27.2 | → **移除**（应用无 API 依赖；消除旧 RedissonConnection 与 Spring Data Redis 3.5 pExpire 递归冲突） |
| tomcat-embed-core | 10.1.19 | → **10.1.56**（10.1.58 尚未发布到 Central，见第 2 节） |
| spring-framework | 6.1.4 | → **6.2.19** |
| jackson-bom | 2.15.4 | → **2.18.11** |
| logback | 1.4.14 | → **1.5.34** |
| netty | 4.1.107 | → **4.1.137.Final** |
| micrometer | 1.12.3 | → **1.15.12** |
| log4j-api | 2.24.3 | → **2.25.5** |
| postgresql | 42.6.1 | → **42.7.12** |
| commons-compress | 1.25.0 | → **1.26.0** |
| commons-lang3 | 3.13.0 | → **3.18.0** |
| poi-ooxml | 5.2.5 | → **5.4.0** |
| opennlp-tools | 1.9.4 | → **1.9.5** |
| follow-redirects（前端） | 1.16.0 | → **1.16.1**（`overrides`） |

效果：Maven 侧漏洞从 145 → 14 → **4 个包（8 条记录）**，且剩余项**全部**属于「需要跨重大架构线升级」或「修复版本未发布」。

## 2. 不可在线内修复项（4 个包 / 8 条记录）

| 包 | 当前 | 修复版本 | 为什么本版本线修不了 |
|---|---|---|---|
| `tomcat-embed-core` | 10.1.56 | 10.1.58 | **修复版本尚未发布**到 Maven Central（发布后门禁会自动变红要求升级） |
| `spring-webmvc` / `spring-webflux` | 6.2.19 | 7.0.9 | **Spring Framework 7**（= Boot 4）才修 |
| `opennlp-tools` | 1.9.5 | 2.5.9 | opennlp 2.x API 不兼容，且由 langchain4j 0.35 传递引入 |

**Boot 3.5 迁移结论**：
v1.3.0 B 批已成功落地 Spring Boot 3.5.16、MyBatis-Plus 3.5.17 与 Sa-Token 1.46.0。此前在 spike 探针中遭遇的 `/auth/login` 触发 `java.lang.StackOverflowError`，经深挖底层调用栈确认：根因为 `redisson-spring-boot-starter:3.27.2` 引入的 `redisson-spring-data-32` 与 Spring Data Redis 3.5.13 的 `DefaultedRedisConnection.pExpire` 存在二进制不兼容与递归调用死循环；同时由于项目中所有分布式限流、分布式锁与会话存储均通过标准 Spring Data Redis / Lua 脚本实现，完全不需要直接依赖 Redisson 客户端。移除 Redisson 并回归原生 Lettuce 驱动后，彻底根除栈溢出缺陷。
此外，针对 Sa-Token 1.46.0 在单测运行中对 `SaManager` 全局上下文的改动，在各 Mockito 测试中实现了 ThreadLocal 上下文的保存与隔离还原（避免只读上下文污染随后的 SpringBoot 集成测试）。全量 216 项单元测试全部通过，架构不变量与 OpenAPI 快照 100% 保持一致。

**暴露面说明**：这些均属框架/库自身缺陷，本项目通过既有缓解措施（挂件签名、限流、租户隔离、最小暴露面）
降低可利用性；不等于"忽略"，`ignoreUntil = 2027-06-30`，到期门禁会重新报出。

## 3. 前端构建期依赖（不进入运行时镜像）

15 条，见 `osv-scanner.toml`：`vite@5.4.21`(8)、`vitest@1.6.1`(1)、`tinypool@0.8.4`(2)、`esbuild@0.21.5`(1)、
`braces@3.0.3`(1)、`postcss-selector-parser@6.1.4`(1)、`source-map-js@1.2.1`(1)。

`frontend/Dockerfile` 运行时阶段只 `COPY --from=build /app/dist`，镜像内不含 `node_modules`；
这些包只在构建/测试期加载本仓库源码，不处理外部输入。修复需 vite 8 / vitest 5 / tailwind 4 全套迁移（v1.2 批次）。

## 4. 复核方式

```bash
# 与 CI 完全一致的判定（需要 osv-scanner 在 PATH）
python3 scripts/check_osv.py
# 期望：必须修复项 0 条，延后修复项 4 项 -> 退出码 0
```