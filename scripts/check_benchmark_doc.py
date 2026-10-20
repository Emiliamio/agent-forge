#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AgentForge Benchmark 文档防漂移校验工具 (scripts/check_benchmark_doc.py)
从 docs/BENCHMARK.md 提取 BENCHMARK-STABLE 隐藏注释指纹，与 docs/benchmark/stable.json 机器基线逐字段比对。
"""

import sys
import json
import re
import argparse

# 确保 UTF-8 输出
if sys.stdout.encoding and sys.stdout.encoding.lower() != 'utf-8':
    try:
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    except Exception:
        pass

def normalize_val(v):
    if isinstance(v, float):
        return round(v, 4)
    if isinstance(v, dict):
        return {k: normalize_val(v[k]) for k in sorted(v.keys())}
    if isinstance(v, list):
        return [normalize_val(x) for x in v]
    return v

def compare_payloads(baseline_data, doc_data):
    b_corpus = normalize_val(baseline_data.get("corpus", {}))
    d_corpus = normalize_val(doc_data.get("corpus", {}))

    b_stable = normalize_val(baseline_data.get("stable", {}))
    d_stable = normalize_val(doc_data.get("stable", {}))

    diffs = []

    # 1. 比对 corpus
    all_corpus_keys = sorted(set(b_corpus.keys()) | set(d_corpus.keys()))
    for k in all_corpus_keys:
        bv = b_corpus.get(k)
        dv = d_corpus.get(k)
        if bv != dv:
            diffs.append((f"corpus.{k}", str(dv), str(bv)))

    # 2. 比对 stable 聚合指标
    metric_keys = ["recall_at_1", "recall_at_3", "recall_at_5", "mrr", "ndcg_at_5"]
    for mk in metric_keys:
        bv = b_stable.get(mk)
        dv = d_stable.get(mk)
        if bv != dv:
            diffs.append((f"stable.{mk}", str(dv), str(bv)))

    # 3. 比对 per_case (按 id 排序后逐项比对 rank 和 hit)
    b_cases = {item.get("id"): item for item in b_stable.get("per_case", [])}
    d_cases = {item.get("id"): item for item in d_stable.get("per_case", [])}
    all_case_ids = sorted(set(b_cases.keys()) | set(d_cases.keys()))

    for cid in all_case_ids:
        bc = b_cases.get(cid, {})
        dc = d_cases.get(cid, {})
        if bc.get("rank") != dc.get("rank") or bc.get("hit") != dc.get("hit"):
            diffs.append((
                f"stable.case[{cid}]",
                f"rank={dc.get('rank')}, hit={dc.get('hit')}",
                f"rank={bc.get('rank')}, hit={bc.get('hit')}"
            ))

    return diffs

def main():
    parser = argparse.ArgumentParser(description="AgentForge Benchmark 文档防漂移校验工具")
    parser.add_argument("--stable", required=True, help="基线 JSON 路径 (docs/benchmark/stable.json)")
    parser.add_argument("--doc", required=True, help="Benchmark 文档路径 (docs/BENCHMARK.md)")
    args = parser.parse_args()

    try:
        with open(args.doc, "r", encoding="utf-8") as f:
            doc_content = f.read()
    except Exception as e:
        print(f"[FAIL] 无法读取文档文件: {args.doc} ({e})", file=sys.stderr)
        sys.exit(1)

    match = re.search(r"<!--\s*BENCHMARK-STABLE:\s*(.*?)\s*-->", doc_content, re.DOTALL)
    if not match:
        print("[FAIL] 请在 docs/BENCHMARK.md 中保留 BENCHMARK-STABLE 指纹（由 render_benchmark.py 生成）", file=sys.stderr)
        sys.exit(1)

    raw_json = match.group(1).strip()
    try:
        doc_data = json.loads(raw_json)
    except Exception as e:
        print(f"[FAIL] 指纹 JSON 解析失败: {e}\n{raw_json}", file=sys.stderr)
        sys.exit(1)

    try:
        with open(args.stable, "r", encoding="utf-8") as f:
            baseline_data = json.load(f)
    except Exception as e:
        print(f"[FAIL] 无法读取基线文件: {args.stable} ({e})", file=sys.stderr)
        sys.exit(1)

    diffs = compare_payloads(baseline_data, doc_data)

    if diffs:
        print("=" * 80)
        print(" [DRIFT DETECTED] docs/BENCHMARK.md 内嵌指标与机器基线发生漂移！")
        print("=" * 80)
        print(f"{'指标项':<24} {'文档值 (Doc)':<26} {'基线值 (Baseline)'}")
        print("-" * 80)
        for field, d_val, b_val in diffs:
            print(f"{field:<24} {d_val:<26} {b_val}")
        print("=" * 80)
        print("说明: 文档数字必须完全由机器自动化生成并与 stable.json 保持一致。请运行 render_benchmark.py 刷新。")
        sys.exit(1)
    else:
        print("[OK] BENCHMARK.md matches stable baseline")
        sys.exit(0)

if __name__ == "__main__":
    main()
