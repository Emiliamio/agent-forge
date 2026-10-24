# 工程质量纪律（强制）

> 本文件是**验收前置条件**：任何批次（人或 AI）在宣称"完成"之前，必须满足下面五条。
> 违反其中任一条，验收直接判不通过，不论功能是否可用。
> 每条后面都附了真实事故案例（本项目 v1.2–v1.3 期间实际发生过的），不是凭空立的规矩。

---

## 纪律 1：说"不可修复"必须带引擎与修复区间证据

判断某条依赖漏洞"能不能在现有基线上修"，**不允许**以 `npm audit` / `osv-scanner` 的"建议升级版本"作为结论，
必须回到两个原始来源：

```bash
# ① 目标版本的运行时约束（决定能不能在现有 Node/Java 基线上用）
npm view <pkg>@<version> engines
mvn -q help:evaluate -Dexpression=<artifact>.version -DforceStdout   # Maven 侧等价物

# ② OSV 给出的真实修复区间（决定最小可修版本）
curl -s -X POST https://api.osv.dev/v1/query \
  -H 'Content-Type: application/json' \
  -d '{"package":{"name":"<pkg>","ecosystem":"npm"},"version":"<current>"}' \
  | python -c "import json,sys; d=json.load(sys.stdin); print(sorted({e['fixed'] for v in d.get('vulns',[]) for a in v.get('affected',[]) for r in a.get('ranges',[]) for e in r.get('events',[]) if 'fixed' in e}))"
```

**写进 `docs/security/VEX.md` 的"不可修"结论，必须附上上述两条命令的原始输出摘要**（版本号 + engines 字符串 + fixed 列表）。

**真实事故（v1.3.1）**：C 批把 `vitest 3.2.6` 残留的 5 条 advisory 判为"必须升 vitest 5，
而 vitest 5 需要 Node ≥22.12，故不可修"。实际 `vitest@4.1.12` 的 engines 是 `^20 || ^22 || >=24`，
一行版本号即可清零。错误来源正是把 `npm audit` 的 `fixAvailable` 当成了"最小可修版本"。

---

## 纪律 2：写进文档的每条命令，必须自己先跑一遍

文档里的"机器验证命令"是给下一个人照抄的。**没跑过就不要写**，尤其注意两类静默失效：

| 陷阱 | 现象 | 正确做法 |
|---|---|---|
| JUnit 方法级过滤 | <!-- doc-commands-ignore: 该行故意展示错误用法 -->`mvn -Dtest=ArchitectureTest#r1_xxx test` 对 `@ArchTest` 静态字段**无法选中**，surefire 会退化成跑整个类，输出仍显示 `BUILD SUCCESS` | 统一 `-Dtest=ArchitectureTest`；规则类测试不做方法级过滤 |
| 容器内 `localhost` | 解析到 `::1`，服务只监听 IPv4 时健康检查假失败 | 健康检查一律 `127.0.0.1` |

**真实事故（v1.2.0）**：`docs/ARCHITECTURE.md` 写了 6 条按方法名精确选择 ArchUnit 规则的命令，
以及 2 条文档宣称存在、实现里根本没有的规则（`mapper↛service`、`entity↛service`）。
复制命令能跑出绿，但那条规则从未被执行。

---

## 纪律 3：新增或修改门禁，必须做反向验证（注入 → 红 → 回滚 → 绿）

任何新增的 ArchUnit 规则、契约测试、覆盖率门禁、依赖门禁、**文档门禁**，**只在通过时绿不算数**，
必须证明它真的能拦住违规。而且要分 **三级** 验证，只做第一级是不够的：

### L1 · 有效性：注入 1 条真实违规 → 必须红 → 回滚 → 必须绿

```bash
# ① 注入（例：让 billing.internal 依赖 security.internal）
# ② mvn -B -Dtest=ArchitectureTest test        → 必须红，且报错点名该规则
# ③ git checkout -- <被改文件>
# ④ 再跑一次                                   → 必须绿
```

报告里要贴**红的那一次输出**（含规则名/违约条目），否则视为未验证。

### L2 · 覆盖率：对门禁覆盖的**每一类载体**分别注入

同一个门禁往往覆盖多种写法，**只测其中一种就等于没测**。必须逐类注入、逐类确认能红：

| 载体类型 | 注入示例 | 必须红的门禁 |
|---|---|---|
| 围栏代码块内的命令 | ```bash 里写 `mvn -Dtest=NoSuchTest#nope test` ``` | `check_doc_commands.py` | <!-- doc-commands-ignore: L2 反例载体，故意写错以验证门禁 -->
| **行内代码**（`…`，常用于表格单元格） | 表格里写 `mvn -Dtest=NoSuchTest#nope test` | 同上 | <!-- doc-commands-ignore: L2 反例载体，故意写错以验证门禁 -->
| 引用了不存在的脚本 | `bash scripts/nope.sh` | 同上 | <!-- doc-commands-ignore: L2 反例载体，故意写错以验证门禁 -->
| 未登记的 npm task | `npm run nope` | 同上 | <!-- doc-commands-ignore: L2 反例载体，故意写错以验证门禁 -->
| 失效的 Markdown 锚点 | `[x](docs/foo.md#nope)` | 同上 | <!-- doc-commands-ignore: L2 反例载体，故意写错以验证门禁 -->
| 未挂 claim 的量化表述 | "响应时间 3ms" | `check_doc_claims.py` |
| 架构规则/契约测试 | 跨域 internal 依赖 / 改端点签名 | `ArchitectureTest` / `OpenApiContractTest` |

同时要**核对门禁自报的覆盖数与实际口径一致**：

```bash
# 门禁说自己检查了 N 条 → 用独立 grep 算一遍，两者必须吻合
grep -rEo -- '-Dtest=[A-Za-z0-9_]+' README.md docs/*.md | wc -l
```

### L3 · 声明边界：门禁必须说清"覆盖什么、不覆盖什么"

- 脚本输出里禁止写"全部可解析/全部通过"这类**绝对化表述**，除非同时打印覆盖口径
  （例如：`[OK] 命令引用全部可解析（围栏 + 行内，共 65 条）`）；
- 新增门禁要在 `docs/ENGINEERING-DISCIPLINE.md` 或脚本 docstring 里写明**已知不覆盖**的部分
  （例如：不校验 `curl` 示例、不校验外部 URL 可达性），避免后人误以为"门禁绿 = 一切正确"。

**真实案例一（v1.2.1 / v1.3.0）**：Rule 11（`slices().matching("com.agentforge.service.(*).internal..")`）
两次都是靠注入探针证明有效的——一次在旧域（agent↔rag），一次在新切分的域（workflow↔market），
第二次还顺带证明了这条通用规则会自动覆盖新增特性域。

**真实案例二（v1.4.0，本纪律 L2 的来源）**：`check_doc_commands.py` 上线时只扫**围栏代码块**，
却输出「命令引用全部可解析（48 条）」——而仓库里实际有 **65 条**命令引用，漏掉的 17 条全在行内代码里
（其中 7 条正是 `docs/CLAIMS.md` 矩阵自己的复现命令）。往 `CLAIMS.md` 表格塞一个不存在的测试类，
门禁照样绿。修复后覆盖 48 → 65，并新增 `<!-- doc-commands-ignore: 原因 -->` 用于合法展示反例。
**教训：门禁的"通过"只对已验证过的载体有效；没验证过的载体等于没门禁。**

---

## 纪律 4：改依赖 = 刷 SBOM + 收紧忽略清单

只要 `backend/pom.xml` 或 `frontend/package*.json` 有实质变化（含版本号、模块拆分、移除依赖）：

```bash
bash scripts/refresh-sbom.sh          # 必须；前端固定 node:20 容器渲染，跨平台口径
git status --short docs/sbom/         # 有变化就提交
python3 scripts/check_osv.py          # 期望：可修复项 0 条
```

并且只允许**收紧**、不允许放开：
- 已修复的条目要从 `osv-scanner.toml` / `.trivyignore` **删掉**；
- 新增豁免必须同时写 `ignoreUntil` + `reason` + `docs/security/VEX.md` 的暴露面判定；
- `scripts/check_osv.py`、`.gitleaks.toml` 不允许为了让构建变绿而修改判定逻辑。

**真实事故（v1.2.0）**：B3 把 `springdoc` 升到 2.8.5 却没刷 SBOM，`SBOM · drift check` 直接红；
刷新后后端组件数 175 → 177 才对齐。

---

## 纪律 5：文件编码与换行

所有文本文件（`*.java` / `*.xml` / `*.json` / `*.yml` / `*.md` / `*.sh`）必须是 **UTF-8 无 BOM、LF**。

```bash
# 快速自检（Windows PowerShell）
$b=[IO.File]::ReadAllBytes($path); if ($b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) { 'HAS BOM' }
```

**真实事故（v1.1.2）**：`pom.xml` / `package.json` / `ci.yml` 被写入 BOM，一次连坐 4 个 CI job：
Maven 报 `Non-parseable POM`、vite 报 `"name"... is not valid JSON`、trivy 步骤脚本 `unexpected EOF`。

---

## 附加：测试隔离

测试类不得依赖其它测试类留下的全局静态状态（典型：MyBatis-Plus `TableInfoHelper`、`TenantContextHolder`）。

```java
@BeforeEach void clean() { TableInfoHelper.remove(Document.class); TenantContextHolder.clear(); }
@AfterAll  static void tearDown() { TableInfoHelper.remove(Document.class); TenantContextHolder.clear(); }
```

**真实事故（v1.2.0）**：`HexagonalPortsAndFakesTest` 先跑会污染 `Document` 的 `TableInfo`，
导致 `DocumentAndDatasetServiceTest` 的 `getById` 返回 null、断言退化成
`Wanted but not invoked … zero interactions`。当时的临时解法是 surefire `reuseForks=false`
（全量 123s），根因修好后恢复共享 JVM（41s）。

---

# 验收清单（贴进 PR 描述逐项打勾）

```markdown
- [ ] 全量后端：`cd backend && mvn -B clean verify -Dlive=true` → Tests run ≥ 216 / Failures 0 / coverage met
- [ ] 全量前端：`cd frontend && npm ci && npm run lint && npm test && npm run build`
- [ ] 架构不变量：`mvn -B -Dtest=ArchitectureTest test` → 12 项全绿
- [ ] 契约门禁：`mvn -B -Dtest=OpenApiContractTest -Dlive=true test` → 绿
- [ ] 依赖门禁：`python3 scripts/check_osv.py` → 可修复项 0
- [ ] SBOM：`bash scripts/refresh-sbom.sh` 后 `git status` 无残差
- [ ] 反向验证 L1：新增门禁的红/绿两次输出已贴（注入 → 红 → 回滚 → 绿）
- [ ] 反向验证 L2：**每类载体**都单独注入过（围栏命令 / 行内代码 / 脚本引用 / npm task / 锚点 / 量化表述），且逐类变红
- [ ] 反向验证 L3：门禁自报的覆盖数与独立 grep 统计吻合（例：65 条 vs `grep -rEo '\-Dtest=[A-Za-z0-9_]+' | wc -l`），输出无"全部"式绝对化表述
- [ ] 文档命令：本次新增/修改的每条验证命令都亲自跑过
- [ ] 编码：改动文件 UTF-8 无 BOM、LF
- [ ] 清场：`git status --short` 为空；临时脚本/日志/容器已清理
```

# 禁止项

1. 用 `@Disabled` / 删断言 / 放宽 `--max-warnings` / 加大 sleep 让测试变绿；
2. 用 `--no-verify` 跳过 gitleaks，或往 `.gitleaks.toml` 加白名单；
3. 在 `osv-scanner.toml` / `.trivyignore` 里加**无 `reason`、无 `ignoreUntil`** 的豁免；
4. 为了让构建通过而修改 `scripts/check_osv.py` 的判定逻辑；
5. 在文档里写没有实际跑过的验证命令，或声称"已修复"但未贴证据。
