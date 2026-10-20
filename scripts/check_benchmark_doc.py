#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AgentForge Benchmark 文档防漂移校验工具 (scripts/check_benchmark_doc.py)
1. 校验 docs/BENCHMARK.md 内嵌的 BENCHMARK-STABLE 指纹与 docs/benchmark/stable.json 机器基线是否强一致。
2. 校验 docs/BENCHMARK.md 正文指标表与 20 组业务提问明细表的 Rank 排名与机器基线是否完全一致。
"""

import sys
import json
import re
import argparse
import tempfile
import os

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

def parse_visible_metrics(doc_content):
    """解析正文核心指标表中的 5 个聚合指标"""
    metrics = {}
    target_keys = {
        "recall@1": "recall_at_1",
        "recall@3": "recall_at_3",
        "recall@5": "recall_at_5",
        "mrr": "mrr",
        "ndcg@5": "ndcg_at_5"
    }
    for line in doc_content.splitlines():
        if not line.strip().startswith("|"):
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 3:
            continue
        label_clean = parts[1].replace("*", "").strip().lower()
        raw_val = parts[2].strip()

        matched_key = None
        if label_clean == "recall@1":
            matched_key = "recall_at_1"
        elif label_clean == "recall@3":
            matched_key = "recall_at_3"
        elif label_clean == "recall@5":
            matched_key = "recall_at_5"
        elif label_clean.startswith("mrr"):
            matched_key = "mrr"
        elif label_clean == "ndcg@5":
            matched_key = "ndcg_at_5"

        if matched_key and matched_key not in metrics:
            val_clean = raw_val.replace("*", "").strip()
            has_percent = "%" in val_clean
            num_str = val_clean.replace("%", "").strip()
            try:
                fval = float(num_str)
                if has_percent:
                    fval /= 100.0
                metrics[matched_key] = round(fval, 4)
            except ValueError:
                raise ValueError(f"无法解析指标 [{matched_key}] 的数值: '{raw_val}'，行原文: {line}")

    missing = set(target_keys.values()) - set(metrics.keys())
    if missing:
        raise ValueError(f"正文中缺少核心指标项: {sorted(missing)}")
    return metrics

def parse_visible_cases(doc_content):
    """解析正文提问明细表中的 Rank 列"""
    rank_idx = None
    cases = {}

    for line in doc_content.splitlines():
        if not line.strip().startswith("|"):
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 3:
            continue

        if rank_idx is None:
            norm_parts = [p.replace("*", "").strip().lower() for p in parts]
            if "序号" in norm_parts and "rank" in norm_parts:
                rank_idx = norm_parts.index("rank")
            continue

        id_str = parts[1].replace("*", "").strip()
        if not id_str.isdigit():
            continue

        cid = int(id_str)
        if len(parts) <= rank_idx:
            raise ValueError(f"明细表行格式异常（列数不足）: {line}")

        rank_raw = parts[rank_idx].replace("*", "").strip()
        m = re.match(r"^#(\d+)$", rank_raw)
        if m:
            rank = int(m.group(1))
        elif rank_raw in ("-", "—", "未命中"):
            rank = 0
        else:
            raise ValueError(f"明细表 Rank 列格式无法识别: '{rank_raw}'，行原文: {line}")

        cases[cid] = rank

    if rank_idx is None:
        raise ValueError("未在正文中找到包含 '序号' 与 'Rank' 的明细表表头")
    return cases

def compare_visible_against_baseline(visible_metrics, visible_cases, baseline_stable):
    diffs = []
    # 1. 比对 5 个聚合指标
    for k in ["recall_at_1", "recall_at_3", "recall_at_5", "mrr", "ndcg_at_5"]:
        v_val = visible_metrics.get(k)
        b_val = normalize_val(baseline_stable.get(k))
        if v_val != b_val:
            v_str = f"{v_val:.4f}" if isinstance(v_val, float) else str(v_val)
            b_str = f"{b_val:.4f}" if isinstance(b_val, float) else str(b_val)
            diffs.append((f"stable.{k}", v_str, b_str))

    # 2. 比对用例数量与逐条 Rank
    b_cases = {item.get("id"): item.get("rank", 0) for item in baseline_stable.get("per_case", [])}
    if len(visible_cases) != len(b_cases):
        diffs.append(("stable.case_count", str(len(visible_cases)), str(len(b_cases))))

    all_cids = sorted(set(visible_cases.keys()) | set(b_cases.keys()))
    for cid in all_cids:
        vrank = visible_cases.get(cid)
        brank = b_cases.get(cid)
        if vrank != brank:
            diffs.append((f"stable.case[{cid}].rank", str(vrank), str(brank)))

    return diffs

def verify_document(doc_content, baseline_data, silent=False):
    # 1. 提取指纹
    match = re.search(r"<!--\s*BENCHMARK-STABLE:\s*(.*?)\s*-->", doc_content, re.DOTALL)
    if not match:
        if not silent:
            print("[FAIL] 请在 docs/BENCHMARK.md 中保留 BENCHMARK-STABLE 指纹（由 render_benchmark.py 生成）")
        return False, [("fingerprint", "missing", "present")]

    raw_json = match.group(1).strip()
    try:
        doc_data = json.loads(raw_json)
    except Exception as e:
        if not silent:
            print(f"[FAIL] 指纹 JSON 解析失败: {e}\n{raw_json}")
        return False, [("fingerprint", "invalid_json", "valid_json")]

    # 2. 校验指纹与 baseline
    fingerprint_diffs = compare_payloads(baseline_data, doc_data)
    if fingerprint_diffs:
        if not silent:
            print("=" * 80)
            print(" [DRIFT DETECTED] docs/BENCHMARK.md 内嵌指标与机器基线发生漂移！")
            print("=" * 80)
            print(f"{'指标项':<24} {'文档值 (Doc)':<26} {'基线值 (Baseline)'}")
            print("-" * 80)
            for field, d_val, b_val in fingerprint_diffs:
                print(f"{field:<24} {d_val:<26} {b_val}")
            print("=" * 80)
            print("说明: 文档数字必须完全由机器自动化生成并与 stable.json 保持一致。请运行 render_benchmark.py 刷新。")
            sys.stdout.flush()
        return False, fingerprint_diffs

    if not silent:
        print("[OK] BENCHMARK.md matches stable baseline")
        sys.stdout.flush()

    # 3. 解析正文表格
    try:
        visible_metrics = parse_visible_metrics(doc_content)
        visible_cases = parse_visible_cases(doc_content)
    except Exception as e:
        if not silent:
            print(f"[FAIL] docs/BENCHMARK.md 正文解析失败: {e}")
            sys.stdout.flush()
        return False, [("visible_parse", str(e), "valid_table")]

    # 4. 比对正文表格与基线
    baseline_stable = baseline_data.get("stable", {})
    visible_diffs = compare_visible_against_baseline(visible_metrics, visible_cases, baseline_stable)
    if visible_diffs:
        if not silent:
            print(f"[FAIL] docs/BENCHMARK.md 正文与机器基线不一致（共 {len(visible_diffs)} 处）：")
            print(f"  {'字段':<28} {'正文值':<13} {'基线值'}")
            for field, v_str, b_str in visible_diffs:
                print(f"  {field:<28} {v_str:<13} {b_str}")
            print("请运行 python scripts/render_benchmark.py --input <fresh.json> 重新生成，不要手改数字。")
            sys.stdout.flush()
        return False, visible_diffs

    if not silent:
        print(f"[OK] 正文表格与隐藏指纹一致（5 项指标 + {len(visible_cases)} 条 case 排名）")
        sys.stdout.flush()
    return True, []

SELF_TEST_BASELINE = {
    "corpus": {"chunks": 2, "documents": 1, "name": "sample"},
    "stable": {
        "recall_at_1": 1.0,
        "recall_at_3": 1.0,
        "recall_at_5": 1.0,
        "mrr": 1.0,
        "ndcg_at_5": 1.0,
        "per_case": [
            {"id": 1, "rank": 1, "hit": True},
            {"id": 2, "rank": 0, "hit": False}
        ]
    }
}

SELF_TEST_COMPLIANT_DOC = """# Sample Benchmark
| 指标名称 | 实测数值 | 门禁标准 | 说明 |
| :--- | :--- | :--- | :--- |
| **Recall@1** | **100.00%** | - | 真实切片精准位于第一顺位召回的比率 |
| **Recall@3** | **100.00%** | - | 真实切片位于前三顺位召回的比率 |
| **Recall@5** | **100.00%** | >= 60.0% | 前 5 个引用片段命中目标切片的比率 |
| **MRR (Mean Reciprocal Rank)** | **1.0000** | >= 0.6000 | 平均倒数排名倒数期望 |
| **NDCG@5** | **1.0000** | - | 归一化折损累计增益 |

| 序号 | 业务类别 | Query | 状态 | Rank | 耗时 |
| :---: | :--- | :--- | :---: | :---: | :--- |
| 01 | 类别1 | 问句1 | ✅ PASS | #1 | 10.0 |
| 02 | 类别2 | 问句2 | 🔭 观测 | - | 20.0 |

<!-- BENCHMARK-STABLE: {"corpus":{"chunks":2,"documents":1,"name":"sample"},"stable":{"mrr":1.0,"ndcg_at_5":1.0,"per_case":[{"hit":true,"id":1,"rank":1},{"hit":false,"id":2,"rank":0}],"recall_at_1":1.0,"recall_at_3":1.0,"recall_at_5":1.0}} -->
"""

def run_self_test():
    with tempfile.TemporaryDirectory() as tmpdir:
        # Case 1: 合规样例通过
        ok, diffs = verify_document(SELF_TEST_COMPLIANT_DOC, SELF_TEST_BASELINE, silent=True)
        if not ok:
            print(f"[FAIL] Self-test case 1 (compliant) expected True, got False: {diffs}", file=sys.stderr)
            sys.exit(1)

        # Case 2: 篡改聚合指标（mrr 改 0.9000）必须失败
        tampered_doc_mrr = SELF_TEST_COMPLIANT_DOC.replace("**1.0000**", "**0.9000**", 1)
        ok, diffs = verify_document(tampered_doc_mrr, SELF_TEST_BASELINE, silent=True)
        if ok or not any("stable.mrr" in d[0] for d in diffs):
            print(f"[FAIL] Self-test case 2 (tamper mrr) expected failure on stable.mrr, got {ok}, {diffs}", file=sys.stderr)
            sys.exit(1)

        # Case 3: 篡改单条 case 排名（rank 0 -> 1）必须失败
        tampered_doc_rank = SELF_TEST_COMPLIANT_DOC.replace("| 02 | 类别2 | 问句2 | 🔭 观测 | - | 20.0 |", "| 02 | 类别2 | 问句2 | 🔭 观测 | #1 | 20.0 |")
        ok, diffs = verify_document(tampered_doc_rank, SELF_TEST_BASELINE, silent=True)
        if ok or not any("stable.case[2].rank" in d[0] for d in diffs):
            print(f"[FAIL] Self-test case 3 (tamper rank) expected failure on case 2 rank, got {ok}, {diffs}", file=sys.stderr)
            sys.exit(1)

        # Case 4: 删除一行明细（count 不符）必须失败
        tampered_doc_del = SELF_TEST_COMPLIANT_DOC.replace("| 02 | 类别2 | 问句2 | 🔭 观测 | - | 20.0 |\n", "")
        ok, diffs = verify_document(tampered_doc_del, SELF_TEST_BASELINE, silent=True)
        if ok or not any("stable.case_count" in d[0] for d in diffs):
            print(f"[FAIL] Self-test case 4 (delete row) expected failure on case_count, got {ok}, {diffs}", file=sys.stderr)
            sys.exit(1)

    print("[OK] self-test passed (4 cases)")
    sys.exit(0)

def main():
    parser = argparse.ArgumentParser(description="AgentForge Benchmark 文档防漂移校验工具")
    parser.add_argument("--self-test", action="store_true", help="运行内置自测试模式")
    parser.add_argument("--stable", help="基线 JSON 路径 (docs/benchmark/stable.json)")
    parser.add_argument("--doc", help="Benchmark 文档路径 (docs/BENCHMARK.md)")
    args = parser.parse_args()

    if args.self_test:
        run_self_test()

    if not args.stable or not args.doc:
        parser.error("非 --self-test 模式下必须同时提供 --stable 和 --doc 参数")

    try:
        with open(args.doc, "r", encoding="utf-8") as f:
            doc_content = f.read()
    except Exception as e:
        print(f"[FAIL] 无法读取文档文件: {args.doc} ({e})", file=sys.stderr)
        sys.exit(1)

    try:
        with open(args.stable, "r", encoding="utf-8") as f:
            baseline_data = json.load(f)
    except Exception as e:
        print(f"[FAIL] 无法读取基线文件: {args.stable} ({e})", file=sys.stderr)
        sys.exit(1)

    ok, _ = verify_document(doc_content, baseline_data, silent=False)
    sys.exit(0 if ok else 1)

if __name__ == "__main__":
    main()
