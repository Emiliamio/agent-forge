#!/usr/bin/env python3
"""OSV 依赖漏洞门禁（可审计版）

判定规则（只有一条，机器可判、不含人工白名单）：
  某条漏洞如果存在**同一大版本线内、且已在 Maven Central / npm 发布**的修复版本，
  说明是我们落后于发布，构建立即失败（必须升上去）。
  如果修复版本只存在于更新的大版本线（如 Spring 6.2 -> 7.0、Boot 3.4 -> 3.5/4.x、
  opennlp 1.9 -> 2.x），或修复版本尚未发布，则不属于本版本线可修复项：
  打印清单并放行，清单由 docs/security/VEX.md 记录，随大版本升级批次处理。

用法（CI 与本地一致）：
    python3 scripts/check_osv.py
依赖：osv-scanner 在 PATH 中（CI 已安装），可访问 api.osv.dev。
"""

import json
import re
import subprocess
import sys
import urllib.error
import urllib.request

OSV_TOML = "osv-scanner.toml"
CENTRAL = "https://repo.maven.apache.org/maven2/{path}/{ver}/{artifact}-{ver}.pom"
NPM_REGISTRY = "https://registry.npmjs.org/{name}/{ver}"


def verkey(v: str):
    return [int(x) if x.isdigit() else 0 for x in re.split(r"[.\-+]", v)[:5]]


def same_minor_line(a: str, b: str) -> bool:
    """同一 minor 线视为「补丁级可修」；跨 minor/大版本视为需要计划的升级批次。"""
    ka, kb = verkey(a), verkey(b)
    return len(ka) >= 2 and len(kb) >= 2 and ka[0] == kb[0] and ka[1] == kb[1]


def run_osv() -> dict:
    cmd = ["osv-scanner", "scan", "--format", "json", "--config=" + OSV_TOML, "-r", "."]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if not proc.stdout.strip():
        print("[FAIL] osv-scanner 无输出，stderr:", proc.stderr[-800:], file=sys.stderr)
        sys.exit(1)
    return json.loads(proc.stdout)


def query_fixed(name: str, version: str, ecosystem: str):
    """返回该 package@version 命中漏洞的修复版本集合。"""
    body = json.dumps({"package": {"name": name, "ecosystem": ecosystem}, "version": version}).encode()
    req = urllib.request.Request(
        "https://api.osv.dev/v1/query", data=body, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.loads(resp.read())
    fixed = set()
    for vuln in data.get("vulns", []):
        for aff in vuln.get("affected", []):
            for rng in aff.get("ranges", []):
                for ev in rng.get("events", []):
                    if "fixed" in ev:
                        fixed.add(ev["fixed"])
    return fixed


def exists_on_registry(name: str, version: str, ecosystem: str) -> bool:
    if ecosystem == "Maven":
        group, artifact = name.split(":", 1)
        url = CENTRAL.format(path=group.replace(".", "/"), artifact=artifact, ver=version)
    elif ecosystem == "npm":
        url = NPM_REGISTRY.format(name=name, ver=version)
    else:
        return False
    try:
        urllib.request.urlopen(url, timeout=30)
        return True
    except urllib.error.HTTPError:
        return False
    except Exception:  # noqa: BLE001
        return False


def main() -> int:
    if "--from-json" in sys.argv:
        # 离线校验用：直接读 osv-scanner 的 JSON 输出（CI 走实时扫描）
        path = sys.argv[sys.argv.index("--from-json") + 1]
        with open(path, encoding="utf-8-sig") as f:
            report = json.load(f)
    else:
        report = run_osv()
    fixable, deferred = [], []

    for result in report.get("results", []):
        for pkg in result.get("packages", []):
            info = pkg.get("package", {})
            name, version = info.get("name"), info.get("version")
            ecosystem = info.get("ecosystem")
            if not name or not version:
                continue
            vulns = pkg.get("vulnerabilities") or []
            if not vulns:
                continue
            try:
                fixed_all = query_fixed(name, version, ecosystem)
            except Exception as exc:  # noqa: BLE001
                print(f"[FAIL] 无法向 OSV 查询 {name}@{version}: {exc}", file=sys.stderr)
                return 1
            cur = verkey(version)
            per_advisory = []
            for vuln in vulns:
                fixes = set()
                for aff in vuln.get("affected", []):
                    for rng in aff.get("ranges", []):
                        for ev in rng.get("events", []):
                            if "fixed" in ev:
                                fixes.add(ev["fixed"])
                greater = [f for f in fixes if verkey(f) > cur]
                if greater:
                    per_advisory.append(min(greater, key=verkey))
            if not per_advisory:
                deferred.append((name, version, "OSV 未给出同线修复版本", ecosystem))
                continue
            target = max(per_advisory, key=verkey)
            published = exists_on_registry(name, target, ecosystem)
            if same_minor_line(version, target) and published:
                fixable.append((name, version, target, ecosystem))
            else:
                reason = [] if same_minor_line(version, target) else ["修复版本跨 minor/大版本线"]
                if not published:
                    reason.append("修复版本尚未发布")
                deferred.append((name, version, target + "（" + "、".join(reason) + "）", ecosystem))

    if fixable:
        print("[FAIL] 存在可直接升级修复的依赖漏洞（同大版本线内已有发布版本）：")
        for name, ver, target, eco in fixable:
            print(f"  - {eco:6} {name} {ver} -> {target}")
        print("处理：在 backend/pom.xml 的 properties/dependencyManagement 里钉到上面的版本后重跑。")
        return 1

    if deferred:
        print("[WARN] 以下漏洞的修复版本只在新大版本线或尚未发布，属本版本线不可修复项：")
        for name, ver, target, eco in deferred:
            print(f"  - {eco:6} {name} {ver} -> {target}")
        print("处置：记录在 docs/security/VEX.md 的「不可在线内修复」清单，随大版本升级批次处理。")

    print(f"[OK] OSV 门禁通过：可修复项 0 条，不可在线内修复项 {len(deferred)} 条")
    return 0


if __name__ == "__main__":
    sys.exit(main())