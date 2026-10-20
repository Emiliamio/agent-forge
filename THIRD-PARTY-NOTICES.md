# 第三方开源组件与许可声明 (Third-Party Notices)

**AgentForge Enterprise Platform**  
**Copyright (c) 2026 郑锦城（Emiliamio）<mio2110767128@163.com>**  
**All Rights Reserved.**

---

## 1. 概述与合规声明

AgentForge 商业版采用**私有商业授权（Proprietary License with Evaluation Grant）**。本项目在构建与运行时集成了部分采用宽松开源许可证（Permissive Open-Source Licenses）分发的第三方开源依赖库。

根据软件供应链合规审查与 CycloneDX SBOM（Software Bill of Materials）规范化审计：
1. **零强传染性开源协议（0 GPL / 0 AGPL）**：本项目未静态链接或依赖任何 GPLv3、AGPL 或其他强传染性 Copyleft 许可证组件。
2. **双许可证明确选型（Dual-License Election）**：对于提供多许可证选型的组件（如 JSqlParser 提供 Apache-2.0 与 LGPL-2.1 双许可证，Logback 提供 EPL-1.0 与 LGPL-2.1 双许可证），本项目严格依照其官方授权协议明确选择并适用其**非强传染性宽松许可条款（Apache-2.0 / EPL）**。
3. **接口动态链接（Dynamic API Linking）**：Jakarta 相关 API（`jakarta.annotation-api`、`jakarta.servlet-api`）均遵循带有 **Classpath Exception** 的标准开源协议，允许独立商业软件正常编译与动态链接。

---

## 2. 后端核心依赖清单 (Java / Spring Boot 生态)

通过 CycloneDX Maven 插件（`cyclonedx-maven-plugin:2.8.0`）针对 `backend/pom.xml` 进行全量依赖解析，共审计 172 项构件，主流组件清单如下：

| 组件名称 (Artifact) | 组织 / 项目 (Group) | 采用许可证 (License) | 用途说明 |
| :--- | :--- | :--- | :--- |
| **Spring Boot Stack** (`3.2.3`) | `org.springframework.boot` | Apache-2.0 | 核心服务容器与微服务框架 |
| **MyBatis-Plus** (`3.5.5`) | `com.baomidou` | Apache-2.0 | ORM 持久层与多租户分表分库插件 |
| **Sa-Token** (`1.37.0`) | `cn.dev33` | Apache-2.0 | 企业级轻量权限认证与会话管理 |
| **LangChain4j** (`0.27.1`) | `dev.langchain4j` | Apache-2.0 | 大语言模型抽象层与 Prompt 治理 |
| **Redisson** (`3.27.0`) | `org.redisson` | Apache-2.0 | 分布式锁、滑动窗口限流与分布式缓存 |
| **Apache POI** (`5.2.5`) | `org.apache.poi` | Apache-2.0 | Office 格式（Word/Excel）文档解析 |
| **Apache PDFBox** (`2.0.30`) | `org.apache.pdfbox` | Apache-2.0 | PDF 文本结构化提取与切片 |
| **fastjson2** (`2.0.46`) | `com.alibaba.fastjson2` | Apache-2.0 | 高性能 JSON 序列化引擎 |
| **Hutool** (`5.8.25`) | `cn.hutool` | MulanPSL-2.0 | Java 通用工具库（木兰宽松许可证） |
| **pgvector-java** (`0.1.4`) | `com.pgvector` | MIT | PostgreSQL pgvector 扩展驱动适配 |
| **PostgreSQL JDBC** (`42.6.1`) | `org.postgresql` | BSD-2-Clause | 关系型数据库底层连接驱动 |
| **JSqlParser** (`4.6`) | `com.github.jsqlparser` | Apache-2.0 (Dual LGPL) | SQL 语法树解析与租户字段自动拼接 |
| **Logback** (`1.4.14`) | `ch.qos.logback` | EPL-1.0 (Dual LGPL) | 结构化日志门面实现 |
| **Jakarta Annotations / Servlet** | `jakarta.*` | EPL-2.0 / Classpath Ex | 标准 Java 企业级规范接口定义 |

---

## 3. 前端核心依赖清单 (Vue 3 / Vite 生态)

通过 npm SBOM（`npm sbom --sbom-format cyclonedx`）针对 `frontend/package.json` 进行全量依赖图谱审计，共审计 348 项依赖，许可证分布为：
- **MIT 许可证**：283 个组件（占 81.3%）
- **Apache-2.0 许可证**：11 个组件
- **ISC 许可证**：30 个组件
- **BSD 许可证**：14 个组件
- **BlueOak / CC-BY**：7 个组件
- **GPL / AGPL 组件数**：**0**

| 组件名称 (Package) | 采用许可证 (License) | 用途说明 |
| :--- | :--- | :--- |
| **vue** (`^3.4.19`) | MIT | 核心响应式前端视图框架 |
| **vue-router** (`^4.3.0`) | MIT | 单页面前端客户端路由驱动 |
| **vite** (`^5.1.3`) | MIT | 新一代极速开发服务器与打包构建工具 |
| **tailwindcss** (`^3.4.1`) | MIT | 原子化实用类 CSS 样式体系与暗黑美学引擎 |
| **axios** (`^1.6.7`) | MIT | 基于 Promise 的网络请求客户端 |
| **lucide-vue-next** (`^0.330.0`) | ISC (Permissive) | 工业级现代化 SVG 矢量图标库 |
| **vitest** (`^1.6.0`) | MIT | 基于 Vite 的极速单测执行框架 |
| **eslint** (`^8.57.0`) | MIT | 前端代码静态规范检查工具 |

---

## 4. 完整许可证文本索引与溯源

第三方组件的完整许可证文本与版权归属声明，均可在各组件官方代码仓库或发行构件（JAR 文件与 npm 包内附带的 `META-INF/LICENSE`、`LICENSE`）中查阅。

AgentForge 对所有第三方开源贡献者表达最崇高的敬意与感谢。
