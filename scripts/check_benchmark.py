#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AgentForge Benchmark 防漂移门禁比对工具 (scripts/check_benchmark.py)
比对基线 (baseline) 与当前测试产物 (current) 的 corpus 与 stable 指标段。
逐字段 round(x, 4) 校验；若发生漂移则输出差异表格并 exit(1)。
"""

import sys
import json
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

def compare_sections(baseline_data, current_data):
    """提取 corpus 与 stable 并比对差异"""
    b_corpus = normalize_val(baseline_data.get("corpus", {}))
    c_corpus = normalize_val(current_data.get("corpus", {}))

    b_stable = normalize_val(baseline_data.get("stable", {}))
    c_stable = normalize_val(current_data.get("stable", {}))

    diffs = []

    # 1. 比对 corpus
    all_corpus_keys = sorted(set(b_corpus.keys()) | set(c_corpus.keys()))
    for k in all_corpus_keys:
        bv = b_corpus.get(k)
        cv = c_corpus.get(k)
        if bv != cv:
            diffs.append((f"corpus.{k}", str(bv), str(cv)))

    # 2. 比对 stable 聚合指标
    metric_keys = ["recall_at_1", "recall_at_3", "recall_at_5", "mrr", "ndcg_at_5"]
    for mk in metric_keys:
        bv = b_stable.get(mk)
        cv = c_stable.get(mk)
        if bv != cv:
            diffs.append((f"stable.{mk}", str(bv), str(cv)))

    # 3. 比对 per_case
    b_cases = {item.get("id"): item for item in b_stable.get("per_case", [])}
    c_cases = {item.get("id"): item for item in c_stable.get("per_case", [])}
    all_case_ids = sorted(set(b_cases.keys()) | set(c_cases.keys()))

    for cid in all_case_ids:
        bc = b_cases.get(cid, {})
        cc = c_cases.get(cid, {})
        if bc.get("rank") != cc.get("rank") or bc.get("hit") != cc.get("hit"):
            diffs.append((
                f"stable.case[{cid}]",
                f"rank={bc.get('rank')}, hit={bc.get('hit')}",
                f"rank={cc.get('rank')}, hit={cc.get('hit')}"
            ))

    return diffs

def main():
    parser = argparse.ArgumentParser(description="AgentForge Benchmark 防漂移检测工具")
    parser.add_argument("--baseline", required=True, help="基线 JSON 路径 (docs/benchmark/stable.json)")
    parser.add_argument("--current", required=True, help="当前运行输出 JSON 路径")
    args = parser.parse_args()

    try:
        with open(args.baseline, "r", encoding="utf-8") as f:
            baseline_data = json.load(f)
    except Exception as e:
        print(f"[FAIL] 无法读取基线文件: {args.baseline} ({e})", file=sys.stderr)
        sys.exit(1)

    try:
        with open(args.current, "r", encoding="utf-8") as f:
            current_data = json.load(f)
    except Exception as e:
        print(f"[FAIL] 无法读取当前文件: {args.current} ({e})", file=sys.stderr)
        sys.exit(1)

    diffs = compare_sections(baseline_data, current_data)

    if diffs:
        print("=" * 80)
        print(" [DRIFT DETECTED] Benchmark 检索指标与基线发生漂移！")
        print("=" * 80)
        print(f"{'指标项':<24} {'基线值 (Baseline)':<26} {'当前值 (Current)'}")
        print("-" * 80)
        for field, b_val, c_val in diffs:
            print(f"{field:<24} {b_val:<26} {c_val}")
        print("=" * 80)
        print("说明: 基线是稳定指标的机器门禁。如需调整，必须经评审后重新生成 stable.json，严禁擅自修改。")
        sys.exit(1)
    else:
        print("[OK] No drift detected: corpus and stable metrics match baseline perfectly.")
        sys.exit(0)

if __name__ == "__main__":
    main()
