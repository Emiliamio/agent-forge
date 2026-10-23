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
| vite（前端） | 5.4.21 | → **6.4.4**（清空全部 Vite 5 历史漏洞） |
| vitest（前端） | 1.6.1 | → **3.2.6**（清空 Vitest 1.x 历史漏洞） |
| tailwindcss（前端） | 3.4.1 | → **4.3.3**（迁移至 Tailwind 4，移除 postcss/autoprefixer，消除 braces 传递漏洞） |
| eslint-plugin-vue（前端） | 9.27.0 | → **10.11.1**（适配 ESLint 8.57 与 Vue3 规范） |
| source-map-js（前端） | 1.2.1 | → **1.2.2**（`overrides` 消除 ReDoS 漏洞 GHSA-68fv-2mgg-jv7q） |

效果：Maven 侧漏洞从 145 → 14 → **4 个包（8 条记录）**，且剩余项**全部**属于「需要跨重大架构线升级」或「修复版本未发布」。前端侧消除 6 个包 / 14 条历史漏洞。

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

## 3. 前端构建期依赖 ✅ 已完成（v1.3.1）

### 3.1 最终落位版本

| 包 | 原版本 | 现版本 | 说明 |
|---|---|---|---|
| `vite` | 5.4.21 | **6.4.4** | Node `^18 \|\| ^20 \|\| >=22`，守住 Node 20 基线 |
| `vitest` | 1.6.1 | **4.1.12** | Node `^20 \|\| ^22 \|\| >=24`；连带移除 `tinypool` 依赖（原 9.5 分两条随之消失） |
| `@vitest/mocker` | 3.2.6 | **4.1.12** | 随 vitest 4 |
| `tailwindcss` | 3.4.1 | **4.3.3** | 搭配 `@tailwindcss/vite: 4.3.3`，彻底移除 `postcss` / `autoprefixer` |
| `eslint-plugin-vue` | 9.27.0 | **10.11.1** | 保持 ESLint 8.57 兼容（peer `^8.57 \|\| ^9 \|\| ^10`），不必做 flat config 迁移 |
| `source-map-js` | 1.2.1 | **1.2.2** | 随 vite 6；如仍偏低用 `overrides` 钉 |

### 3.2 已消除的 advisory（全部 `RESOLVED`，OSV 实测 `No issues found`）

`vite@5.4.21` 的 8 条（`GHSA-4w7w-66w2-5vf9`、`GHSA-587h-7wmj-h5pm`、`GHSA-698v-4r3x-3vcv`、
`GHSA-9jrq-w75r-8gcw`、`GHSA-fx2h-pf6j-xcff`、`GHSA-r825-w8mm-5pxw`、`GHSA-rq7h-c2jc-7f22`、
`GHSA-v6wh-96g9-6wx3`）、`vitest@1.6.1` 的 `GHSA-5xrq-8626-4rwp`、`esbuild@0.21.5` 的 `GHSA-67mh-4wv8-2f99`、
`postcss-selector-parser@6.1.4` 的 `GHSA-rj75-hqrm-r3gf`、`source-map-js@1.2.1` 的 `GHSA-68fv-2mgg-jv7q`、
`tinypool@0.8.4` 的 `GHSA-5gmw-xhrv-c9v3` / `GHSA-85c8-ppgw-ccpr`，以及 `vitest@3.2.6` 阶段残留的
`GHSA-82fw-gwwq-j7x9` / `GHSA-qxg6-89mg-rqv8` —— 全部清零。

### 3.3 门禁现状（收紧，不是放宽）

- `osv-scanner.toml`：**已无任何豁免条目**（仅保留说明性注释），前端 0 条 advisory；
- `.trivyignore`：**只剩后端 7 个 CVE id**（Spring 7.0.9 / opennlp 2.5.9 / tomcat 10.1.58 未发布），前端条目全删；
- 复核命令与期望输出：

```bash
docker run --rm -w /repo -v "$PWD:/repo" ghcr.io/google/osv-scanner:v1.9.2 \
  scan --format table --sbom docs/sbom/frontend-bom.json
# 期望：No issues found

docker run --rm -v "$PWD:/repo" aquasec/trivy:latest sbom --severity HIGH,CRITICAL \
  --ignore-unfixed --ignorefile .trivyignore --scanners vuln --exit-code 1 docs/sbom/frontend-bom.json
# 期望：exit 0（Clean）
```

### 3.4 一次判断纠错的记录

C 批初版把残留 5 条写成"必须升 `vitest@5`，而 vitest 5 需要 Node ≥22.12，故不可修"。
这个结论**不成立**：`vitest@4.1.12` 的 engines 是 `^20 || ^22 || >=24`，在 Node 20 上即可运行，
且实测能将前端 advisory 清零、`lint/test/build` 全绿。
教训：`npm audit` 给的 `fixAvailable` 只是"最新版本建议"，不是"最小可修版本"；
判断能否在现有 Node 基线上修复，必须回到 engines 与 OSV 的 `fixed` 区间做交叉验证。

## 4. 复核方式

```bash
# 与 CI 完全一致的判定（需要 osv-scanner 在 PATH）
python3 scripts/check_osv.py
# 期望：必须修复项 0 条，延后修复项 4 项 -> 退出码 0
```
