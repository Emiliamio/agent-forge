#!/usr/bin/env python3
"""公开文档量化表述门禁 (check_doc_claims.py)

规则：
1. 扫描公开文档：README.md, README_EN.md, SECURITY.md, THIRD-PARTY-NOTICES.md
2. 排除代码块、行内代码、URL、版本号、端口、日期等干扰
3. 匹配量化/绝对化表述：数字+单位(%/ms/RPM/QPS/条/个/倍/路/并发/万/次) 或 100%|零|全部|完全
4. 命中的段落必须满足：
   - 携带 <!-- claim:N --> (N 为 1..12)
   - 包含指向 docs/CLAIMS.md 的链接
   - 携带 <!-- claims-ignore: 原因 --> (原因 >= 6 字，单文件 <= 5 处)
"""

import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

TARGET_FILES = [
    "README.md",
    "README_EN.md",
    "SECURITY.md",
    "THIRD-PARTY-NOTICES.md",
]

# 需识别的量化与绝对化表述模式
QUANT_PATTERN = re.compile(
    r"\d+(?:\.\d+)?\s*(?:%|ms|RPM|QPS|条|个|倍|路|并发|万|次)|100%|零|全部|完全"
)

# 允许的已登记 claim 集合 (1..12)
VALID_CLAIMS = {str(i) for i in range(1, 13)} | {f"{i:02d}" for i in range(1, 13)}


def clean_text_for_matching(text: str) -> str:
    """清理行内代码、链接目标、版本号、日期、端口等非宣称干扰文本。"""
    # 移除行内代码 `...`
    s = re.sub(r"`[^`\n]+`", "", text)
    # 移除 markdown 链接及图片 URL：[text](url) -> text, ![alt](url) -> alt
    s = re.sub(r"!?\[([^\]]*)\]\([^)]+\)", r"\1", s)
    # 移除裸 URL
    s = re.sub(r"https?://\S+", "", s)
    # 移除版本号与标签 (如 v1.3.1, 3.5.16, 2.0.46)
    s = re.sub(r"\bv\d+\.\d+\S*", "", s)
    s = re.sub(r"\b\d+\.\d+\.\d+(?:\S*)?", "", s)
    # 移除日期 (如 2026-10-23)
    s = re.sub(r"\b20\d{2}-\d{2}-\d{2}\b", "", s)
    # 移除端口号 (常见 4~5 位端口如 8080, 3000, 5432, 6379, 10001)
    s = re.sub(r":?\b\d{4,5}\b", "", s)
    return s


def check_file(filepath: str) -> tuple[list[str], int]:
    """检查单个文件，返回 (错误信息列表, 有效标记总数)。"""
    if not os.path.exists(filepath):
        return [f"[FAIL] 文件不存在: {filepath}"], 0

    with open(filepath, "r", encoding="utf-8") as f:
        raw_text = f.read()

    lines = raw_text.split("\n")
    clean_lines = []
    in_fence = False

    # 消除围栏代码块内部行，保留原始换行以保持行号精确对齐
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("```"):
            in_fence = not in_fence
            clean_lines.append("")
        elif in_fence:
            clean_lines.append("")
        else:
            clean_lines.append(line)

    # 拆分段落（以空行分隔），并记录每个段落的起始行号（1-based）
    paragraphs = []
    current_para = []
    para_start_line = 1

    for idx, line in enumerate(clean_lines, start=1):
        if not line.strip():
            if current_para:
                paragraphs.append((para_start_line, "\n".join(current_para)))
                current_para = []
        else:
            if not current_para:
                para_start_line = idx
            current_para.append(line)
    if current_para:
        paragraphs.append((para_start_line, "\n".join(current_para)))

    errors = []
    file_ignore_count = 0
    file_claim_annotations = 0

    for start_line, para in paragraphs:
        cleaned_para = clean_text_for_matching(para)
        matches = QUANT_PATTERN.findall(cleaned_para)
        if not matches:
            continue

        # 检查段落中的标记
        claim_matches = re.findall(r"<!--\s*claim:(\d+)\s*-->", para)
        claims_link = bool(re.search(r"CLAIMS\.md", para))
        ignore_matches = re.findall(r"<!--\s*claims-ignore:\s*([^>]+?)\s*-->", para)

        # 校验 claim 编号合法性
        for c in claim_matches:
            if c not in VALID_CLAIMS:
                errors.append(
                    f"[FAIL] {filepath}:{start_line} 无效的 claim 编号: <!-- claim:{c} --> (必须在 1..12 范围内)"
                )
            else:
                file_claim_annotations += 1

        if claims_link:
            file_claim_annotations += 1

        # 校验 claims-ignore 规范
        for reason in ignore_matches:
            file_ignore_count += 1
            if len(reason.strip()) < 6:
                errors.append(
                    f"[FAIL] {filepath}:{start_line} claims-ignore 原因过短: \"{reason.strip()}\" (至少需要 6 个字)"
                )

        # 若没有任何合规标记，判定为违规
        if not claim_matches and not claims_link and not ignore_matches:
            first_match = matches[0]
            errors.append(
                f'[FAIL] {filepath}:{start_line} 未登记的量化表述："{first_match}"\n'
                f"       修复：改为「…（见 docs/CLAIMS.md#claim-09）」或加 <!-- claim:N -->，确属说明性文字用 <!-- claims-ignore: 原因 -->"
            )

    if file_ignore_count > 5:
        errors.append(
            f"[FAIL] {filepath} claims-ignore 数量超标: {file_ignore_count} 处 (单文件上限 ≤ 5 处)"
        )

    return errors, (file_claim_annotations + file_ignore_count)


def main() -> int:
    all_errors = []
    total_annotations = 0

    for fn in TARGET_FILES:
        errs, count = check_file(fn)
        all_errors.extend(errs)
        total_annotations += count

    if all_errors:
        for err in all_errors:
            print(err, file=sys.stderr)
        return 1

    print(f"[OK] 公开文档量化表述均已挂 claim 或已登记豁免（共 {total_annotations} 处）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
