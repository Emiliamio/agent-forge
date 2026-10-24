#!/usr/bin/env python3
"""文档命令与链接引用校验门禁 (check_doc_commands.py)

校验规则：
1. 范围：README.md, README_EN.md, CONTRIBUTING.md, docs/**/*.md (含 ADR)
2. 围栏代码块中的可执行命令校验（跳过以 # 开头的注释行）：
   - -Dtest=ClassName#methodName: 类存在且包含该方法或 @ArchTest 规则字段
   - -Dtest=ClassName: 类存在
   - bash scripts/xxx.sh 或 ./scripts/xxx.sh: 脚本文件存在
   - python[3] scripts/xxx.py: 脚本文件存在
   - npm run <task>: frontend/package.json 的 scripts 中包含该 task
3. 文档间带锚点链接校验 (docs/xxx.md#anchor 或 xxx.md#anchor)：
   - 目标文件存在
   - 锚点匹配目标文件的标题 GitHub slug 或 <a id/name="...">
"""

import glob
import json
import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def github_slug(title: str) -> str:
    """按 GitHub 规则生成标题锚点 slug。"""
    t = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", title)
    t = re.sub(r"[*_`]", "", t)
    t = t.strip().lower()
    t = re.sub(r"[^\w\s\-]", "", t)
    t = re.sub(r"[\s_]+", "-", t)
    return t.strip("-")


def get_file_anchors(filepath: str) -> set[str]:
    """提取 Markdown 文件中所有的合法锚点（标题 slug + HTML a 标签 id/name）。"""
    if not os.path.exists(filepath):
        return set()
    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()

    anchors = set()
    # 提取标题
    for line in content.split("\n"):
        line = line.strip()
        if line.startswith("#"):
            title = re.sub(r"^#+\s*", "", line)
            slug = github_slug(title)
            if slug:
                anchors.add(slug)

    # 提取显式 HTML anchor
    for match in re.finditer(r'<a\s+[^>]*(?:id|name)=["\']([^"\']+)["\']', content, re.IGNORECASE):
        anchors.add(match.group(1).lower())

    return anchors


def load_test_classes() -> dict[str, str]:
    """加载 backend 测试类源码映射：ClassName -> 文件内容。"""
    classes = {}
    test_dir = os.path.join(REPO_ROOT, "backend", "src", "test", "java")
    if not os.path.exists(test_dir):
        return classes
    for root, _, files in os.walk(test_dir):
        for f in files:
            if f.endswith(".java"):
                cname = f[:-5]
                fpath = os.path.join(root, f)
                with open(fpath, "r", encoding="utf-8", errors="ignore") as jf:
                    classes[cname] = jf.read()
    return classes


def load_npm_scripts() -> set[str]:
    """加载 frontend/package.json 中的 scripts task 名称。"""
    pkg_path = os.path.join(REPO_ROOT, "frontend", "package.json")
    if not os.path.exists(pkg_path):
        return set()
    with open(pkg_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    return set(data.get("scripts", {}).keys())


def check_all_documents() -> tuple[list[str], int]:
    test_classes = load_test_classes()
    npm_scripts = load_npm_scripts()

    pattern_files = [
        "README.md",
        "README_EN.md",
        "CONTRIBUTING.md",
    ]
    pattern_files += glob.glob("docs/**/*.md", recursive=True)
    target_files = sorted({os.path.normpath(f) for f in pattern_files if os.path.isfile(f)})

    test_pat = re.compile(r"-Dtest=([A-Za-z0-9_]+)(?:#([A-Za-z0-9_]+))?")
    sh_pat = re.compile(r"(?:bash\s+|(?:\./))([a-zA-Z0-9_\-\/]+\.sh)\b")
    py_pat = re.compile(r"python(?:3)?\s+([a-zA-Z0-9_\-\/]+\.py)\b")
    npm_pat = re.compile(r"npm\s+run\s+([a-zA-Z0-9_\-:]+)\b")
    md_link_pat = re.compile(r"\[[^\]]*\]\(([^)\s]+\.md#[^)\s]+)\)")

    errors = []
    checked_count = 0
    anchors_cache: dict[str, set[str]] = {}

    for doc_path in target_files:
        with open(doc_path, "r", encoding="utf-8", errors="ignore") as f:
            lines = f.readlines()

        in_fence = False
        doc_dir = os.path.dirname(doc_path)

        for line_no, raw_line in enumerate(lines, start=1):
            line = raw_line.strip()
            if line.startswith("```"):
                in_fence = not in_fence
                continue

            # 围栏代码块内部检查可执行命令
            if in_fence:
                if line.startswith("#"):
                    continue

                # 1. -Dtest=
                for m in test_pat.finditer(line):
                    checked_count += 1
                    cname, mname = m.group(1), m.group(2)
                    if cname not in test_classes:
                        errors.append(
                            f"[FAIL] {doc_path}:{line_no} 引用了不存在的测试类: {cname}"
                        )
                    elif mname:
                        content = test_classes[cname]
                        # 支持标准方法 void mname( 或 ArchRule 字段声明
                        has_method = (
                            f"void {mname}(" in content
                            or f"void {mname} (" in content
                            or re.search(rf"\b{mname}\s*=", content) is not None
                        )
                        if not has_method:
                            errors.append(
                                f"[FAIL] {doc_path}:{line_no} 引用了不存在的测试方法/规则: {cname}#{mname}"
                            )

                # 2. bash scripts/xxx.sh
                for m in sh_pat.finditer(line):
                    checked_count += 1
                    spath = m.group(1)
                    full_spath = os.path.join(REPO_ROOT, spath)
                    if not os.path.exists(full_spath):
                        errors.append(
                            f"[FAIL] {doc_path}:{line_no} 引用了不存在的脚本: {spath}"
                        )

                # 3. python scripts/xxx.py
                for m in py_pat.finditer(line):
                    checked_count += 1
                    spath = m.group(1)
                    full_spath = os.path.join(REPO_ROOT, spath)
                    if not os.path.exists(full_spath):
                        errors.append(
                            f"[FAIL] {doc_path}:{line_no} 引用了不存在的脚本: {spath}"
                        )

                # 4. npm run <task>
                for m in npm_pat.finditer(line):
                    checked_count += 1
                    task = m.group(1)
                    if task not in npm_scripts:
                        errors.append(
                            f"[FAIL] {doc_path}:{line_no} 引用了未在 frontend/package.json 登记的 npm task: {task}"
                        )

            # 跨文档带锚点链接校验 (检查所有 Markdown 链接)
            for m in md_link_pat.finditer(raw_line):
                checked_count += 1
                link = m.group(1)
                file_part, anchor_part = link.split("#", 1)
                # 解析目标文件路径（可能是相对当前文档，也可能是相对根路径）
                candidate_paths = [
                    os.path.normpath(os.path.join(doc_dir, file_part)),
                    os.path.normpath(os.path.join(REPO_ROOT, file_part)),
                ]
                resolved_file = None
                for cp in candidate_paths:
                    if os.path.isfile(cp):
                        resolved_file = cp
                        break

                if not resolved_file:
                    errors.append(
                        f"[FAIL] {doc_path}:{line_no} 锚点链接目标文件不存在: {link}"
                    )
                else:
                    if resolved_file not in anchors_cache:
                        anchors_cache[resolved_file] = get_file_anchors(resolved_file)
                    valid_anchors = anchors_cache[resolved_file]
                    clean_anchor = anchor_part.lower().strip()
                    if clean_anchor not in valid_anchors:
                        errors.append(
                            f"[FAIL] {doc_path}:{line_no} 锚点不存在: #{anchor_part} (在目标文件 {resolved_file} 中未找到对应标题或 ID)"
                        )

    return errors, checked_count


def main() -> int:
    errors, total = check_all_documents()
    if errors:
        for err in errors:
            print(err, file=sys.stderr)
        return 1

    print(f"[OK] 文档中的命令引用全部可解析（共 {total} 条）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
