# 📖 AgentForge 商业交付与极速实施手册（买家版）

**版本**：v1.1.0 Commercial Enterprise Release  
**适用对象**：系统管理员、运维工程师、软件集成商交付团队

---

## ⚡ 1. 极速 3 分钟一键启动指南

### 方式 A：Docker Compose 生产一键交付（推荐）
在任意安装了 Docker 的 Linux / Windows 服务器上执行：
```bash
# 1. 启动全栈生产容器 (PostgreSQL 16 pgvector + Redis 7 + 后端 Java 21 + 前端 Nginx)
docker-compose -f docker-compose-prod.yml up -d
```
* **控制台 Web Studio 访问地址**：`http://服务器IP:80`
* **全员 Copilot 极简门户访问地址**：`http://服务器IP:80/copilot`
* **默认管理员账户**：`admin` / `admin123456`
* **默认超级租户 ID**：`1`

### 方式 B：本地源码运行与调试
1. 启动底层存储：
   ```bash
   docker-compose up -d postgres redis
   ```
2. 启动 Spring Boot 3.2 后端：
   ```bash
   cd backend && mvn spring-boot:run
   ```
3. 启动 Vue 3 现代化前端：
   ```bash
   cd frontend && npm install && npm run dev
   ```
   浏览器打开 `http://localhost:3000` 即可进入工作台！

---

## 🛠️ 2. 系统一键自检与运维工具库

### 2.1 生产环境一键自动化巡检
项目内置了生产级一键健康自检脚本，自动检测全链路连通性：
* **Linux / 信创环境**：
  ```bash
  chmod +x scripts/health_check.sh && ./scripts/health_check.sh
  ```
* **Windows Server 环境**：
  双击运行 `scripts\health_check.bat`

### 2.2 数据库与向量索引一键热备份
* **执行备份**：
  ```bash
  chmod +x scripts/backup_database.sh && ./scripts/backup_database.sh
  ```
  自动导出包含 pgvector 向量索引的完整 SQL 并保留最近 7 天历史快照。

### 2.3 自检 API
* **自检接口**：`GET http://localhost:8080/api/system/health`
* **自检范围**：
  - [x] PostgreSQL 16 读写及 pgvector 向量索引健康状态
  - [x] Redis 7 响应延迟及语义降本缓存就绪状态
  - [x] 本地磁盘流式缓冲读写权限
  - [x] Java 21 虚拟线程 (Virtual Threads) 运行时状态

---

## ❓ 3. 常见交付与运维疑难排查 10 问 (FAQ)

#### Q1: 数据库是否需要手动建表和导入 SQL？
* **答**：**完全不需要！** 系统内置了 `Zero-DBA` 自动初始化引擎，首次启动会自动检测并静默执行 `schema.sql` 和 `seed-data.sql`，开箱即用。

#### Q2: 某一个大模型 API Key 突然欠费了，系统会不会报错卡死？
* **答**：**绝对不会！** 系统内置了 `ApiKeyPoolManager` 智能负载池与 `ResilientLlmFailoverService` 级联熔断器，遇到 401/429 异常会自动切换下一个健康 Key，或者自动平滑降级到本地 Ollama 离线显卡模型。

#### Q3: 客户上传了带密码或者损坏的 PDF，会不会把 JVM 内存撑爆？
* **答**：**不会！** 系统采用 `HardenedStreamingDocumentParser` 磁盘流式缓冲，支持 800MB 超大文件流式解析；遇到加密文件 0.1 秒内拦截提示，遇到损坏单页自动隔离至死信队列（DLQ），继续处理其余页面。

#### Q4: 多租户数据真的不会发生跨公司越权泄漏吗？
* **答**：**绝无可能！** 系统基于 MyBatis-Plus 在 JsqlParser SQL AST 抽象语法树编译层级强行注入 `AND tenant_id = ?`，无论是复杂 JOIN 还是子查询，物理级杜绝任何数据泄露风险。

#### Q5: 如何将智能体挂件嵌入到企业现有的 OA 或 CRM 系统？
* **答**：只需在任意第三方网页的 `<body>` 底部粘贴以下两行代码即可：
  ```html
  <script src="http://你的服务器IP/agentforge-widget.js" 
          data-api-url="http://你的服务器IP/api" 
          data-app-id="1" 
          data-title="智能助手"></script>
  ```
  基于 Shadow DOM 技术，100% 物理隔离样式，绝不会污染主站 CSS。

---

## 📁 4. 配套商业交付白皮书库

* [🏛️ 信创国产化软硬件适配兼容性白皮书 (`docs/XINCHUANG_COMPATIBILITY_MATRIX.md`)](./XINCHUANG_COMPATIBILITY_MATRIX.md)
* [🎯 招投标技术专家答辩与现场攻防 20 问宝典 (`docs/BIDDING_DEFENSE_FAQ.md`)](./BIDDING_DEFENSE_FAQ.md)
* [🛡️ 企业级等保三级安全白皮书 (`docs/ENTERPRISE_SECURITY_WHITEPAPER.md`)](./ENTERPRISE_SECURITY_WHITEPAPER.md)
* [📋 招投标技术偏离表与指标对照 (`docs/RFP_TECHNICAL_SPECIFICATION.md`)](./RFP_TECHNICAL_SPECIFICATION.md)
* [📊 企业 AI 落地 ROI 成本效益分析报告 (`docs/ROI_COST_ANALYSIS_REPORT.md`)](./ROI_COST_ANALYSIS_REPORT.md)
* [🚨 企业级高可用容灾与应急预案 SOP (`docs/DISASTER_RECOVERY_SOP.md`)](./DISASTER_RECOVERY_SOP.md)

---

## 📞 5. 商业授权与技术支持 SLA
* 本源码包享有商业闭环永久授权，允许进行二次开发、定制贴牌 (OEM) 及向最终客户私有化交付。
