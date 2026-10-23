# 依赖漏洞处置记录（VEX-lite）

本文件记录 **OSV-Scanner 门禁**中被显式接受的风险项。配套机器可读清单：仓库根 `osv-scanner.toml`。
原则：**生产依赖必须为零告警**；只有「不进入交付产物」的构建期依赖才允许带理由、带到期日的豁免。

门禁位置：`.github/workflows/ci.yml` 的 `security-audit` job
命令：`osv-scanner scan --config=osv-scanner.toml -r ./`
判定：忽略清单之外出现任何漏洞 → 构建立即失败。

---

## 1. 已修复（v1.1.3）

| 组件 | 原版本 | 风险 | 处置 |
|---|---|---|---|
| Spring Boot | 3.2.3 | 连带 Tomcat 10.1.19 / Spring 6.1.4 / Jackson 2.15.4 等约 140 条告警，含多个 CVSS 9.8 | 升级到 **3.2.12**，全部 Maven 告警清零 |
| follow-redirects | 1.16.0 | GHSA-8r9p-f939-6h3c / GHSA-q3jh-wrc6-jgq8（8.2） | `overrides` 固定 **1.16.1** |

## 2. 已接受（构建期依赖，不随镜像发布）

15 条，全部来自前端构建/测试工具链：`vite@5.4.21`(8)、`vitest@1.6.1`(1)、`tinypool@0.8.4`(2)、
`esbuild@0.21.5`(1)、`braces@3.0.3`(1)、`postcss-selector-parser@6.1.4`(1)、`source-map-js@1.2.1`(1)。

**暴露面判定（为什么接受）**

1. 交付产物是静态文件：`frontend/Dockerfile` 的多阶段构建里，运行时阶段只 `COPY --from=build /app/dist /usr/share/nginx/html`，
   镜像内不含 `node_modules`，也不含 vite / vitest / esbuild 任何二进制。
2. 这些包只在「开发机执行 build/test」与「CI 执行 lint/test/build」时加载，不处理不可信输入：
   输入是本仓库源码与锁文件，不是外部请求。
3. 运行时唯一对外服务由 nginx 提供静态资源 + 后端 Spring Boot，与上述包无交集。

**升级成本**：修复需要 vite 8 / vitest 5 / tailwind 4 全套工具链大版本迁移（Tailwind 4 配置模型变更），
属于 v1.2 的独立批次，不适合塞进补丁发行版。

**到期**：`ignoreUntil = 2027-06-30`。到期后 OSV 会重新报出，必须完成升级或再次书面复核。

## 3. 复核方式

```bash
# 本地等价复现（与 CI 同一镜像）
docker run --rm -w /repo \
  -v "$PWD:/repo" ghcr.io/google/osv-scanner:v1.9.2 \
  scan --config=osv-scanner.toml -r .
# 期望：Filtered 15 vulnerabilities from output，退出码 0
```

新增豁免必须同时：① 在 `osv-scanner.toml` 增加 `[[IgnoredVulns]]`（含 `ignoreUntil` 与 `reason`）；
② 在本文件第 2 节补充暴露面判定；③ 说明为何不能在当前版本线内修复。