## 变更类型
- [ ] feat  - [ ] fix  - [ ] test  - [ ] docs  - [ ] ci  - [ ] refactor

## 影响的评审维度
- [ ] 架构设计  - [ ] 代码真实性  - [ ] 可交付度  - [ ] 宣传可信度  - [ ] 安全边界

## 验收证据（勾选并贴关键输出）
- [ ] `mvn test -Dlive=true` → Skipped: 0
- [ ] `mvn verify` → BUILD SUCCESS（含 jacoco / spotbugs）
- [ ] 前端 `lint + test + build` 通过
- [ ] `check_benchmark.py` 无漂移
- [ ] 安全门禁（gitleaks / OSV / trivy）通过

## 影响面与回滚
（迁移变更、配置变更、breaking change 需写明升级步骤）
