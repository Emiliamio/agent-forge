#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AgentForge 机器可读 Benchmark 基准生成器 (scripts/benchmark.py)
执行真实 HTTP 检索测试，生成结构化 JSON 评测快照。
支持 --out 输出全量快照（含软硬件环境与时延），
支持 --stable-out 输出防漂移基线（仅含稳定段，按字节稳定）。
"""

import os
import sys
import json
import time
import platform
import datetime
import argparse
import subprocess

# 确保 UTF-8 输出
if sys.stdout.encoding and sys.stdout.encoding.lower() != 'utf-8':
    try:
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    except Exception:
        pass

from rag_eval_lib import (
    BENCHMARK_CASES,
    check_server_health,
    login_and_get_token,
    prepare_benchmark_corpus,
    fetch_dataset_chunks,
    resolve_ground_truth,
    evaluate_case,
    calculate_ndcg_at_k
)

def get_git_sha():
    """获取当前 HEAD commit short SHA"""
    try:
        res = subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True, check=True)
        return res.stdout.strip()
    except Exception:
        return "unknown"

def get_jdk_version():
    """检测当前 JDK 版本"""
    try:
        res = subprocess.run(["java", "-version"], capture_output=True, text=True)
        out = res.stderr or res.stdout
        first_line = out.splitlines()[0] if out else "OpenJDK 21"
        return first_line.replace('"', '').strip()
    except Exception:
        return "Java 21 LTS"

def get_system_memory_gb():
    """获取系统总内存 GB 数"""
    try:
        import ctypes
        class MEMORYSTATUSEX(ctypes.Structure):
            _fields_ = [
                ("dwLength", ctypes.c_ulong),
                ("dwMemoryLoad", ctypes.c_ulong),
                ("ullTotalPhys", ctypes.c_ulonglong),
                ("ullAvailPhys", ctypes.c_ulonglong),
                ("ullTotalPageFile", ctypes.c_ulonglong),
                ("ullAvailPageFile", ctypes.c_ulonglong),
                ("ullTotalVirtual", ctypes.c_ulonglong),
                ("ullAvailVirtual", ctypes.c_ulonglong),
                ("sullAvailExtendedVirtual", ctypes.c_ulonglong),
            ]
        stat = MEMORYSTATUSEX()
        stat.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
        if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(stat)):
            return int(round(stat.ullTotalPhys / (1024 ** 3)))
    except Exception:
        pass
    try:
        if hasattr(os, "sysconf") and "SC_PAGE_SIZE" in os.sysconf_names and "SC_PHYS_PAGES" in os.sysconf_names:
            return int(round((os.sysconf("SC_PAGE_SIZE") * os.sysconf("SC_PHYS_PAGES")) / (1024 ** 3)))
    except Exception:
        pass
    return 16

def compute_percentile(values, p=95):
    """计算百分位数"""
    if not values:
        return 0.0
    sorted_v = sorted(values)
    idx = int(math_ceil((p / 100.0) * len(sorted_v))) - 1
    idx = max(0, min(idx, len(sorted_v) - 1))
    return sorted_v[idx]

def math_ceil(x):
    import math
    return math.ceil(x)

def run_benchmark(base_url="http://localhost:8080/api", dataset_id=1, force_upload=False):
    """运行全套 benchmark，返回结构化字典"""
    ok, status, health = check_server_health(base_url)
    if not ok:
        raise RuntimeError(f"后端服务不可用 ({base_url}/system/health, status={status})")

    token = login_and_get_token(base_url)
    if not token:
        raise RuntimeError("登录失败，无法获取 Bearer Token")

    auth_headers = {"Authorization": f"Bearer {token}"}
    total_chunks, doc_count = prepare_benchmark_corpus(
        base_url, auth_headers, dataset_id=dataset_id, force_upload=force_upload
    )
    if doc_count == 0:
        doc_count = 1

    dataset_chunks = fetch_dataset_chunks(base_url, auth_headers, dataset_id)
    ground_truth = resolve_ground_truth(BENCHMARK_CASES, dataset_chunks)

    per_case_results = []
    latencies = []

    literal_cases = [c for c in BENCHMARK_CASES if c["is_literal"]]
    literal_count = len(literal_cases)
    literal_hits_1 = 0
    literal_hits_3 = 0
    literal_hits_5 = 0
    literal_rr_sum = 0.0
    literal_ndcg_sum = 0.0

    for case in BENCHMARK_CASES:
        res = evaluate_case(base_url, auth_headers, dataset_id, case, ground_truth, top_k=5)
        latencies.append(res["latency_ms"])
        hit_rank = res["rank"]
        hit = res["hit"]

        if res["is_literal"]:
            if hit and hit_rank == 1:
                literal_hits_1 += 1
            if hit and 1 <= hit_rank <= 3:
                literal_hits_3 += 1
            if hit and 1 <= hit_rank <= 5:
                literal_hits_5 += 1
                literal_rr_sum += (1.0 / hit_rank)
            ndcg = calculate_ndcg_at_k(hit_rank, k=5)
            literal_ndcg_sum += ndcg

        per_case_results.append({
            "id": res["id"],
            "category": res["category"],
            "query": res["query"],
            "is_literal": res["is_literal"],
            "hit": hit,
            "rank": hit_rank if hit else 0,
            "latency_ms": round(res["latency_ms"], 4),
            "top_score": round(res["top_score"], 4)
        })

    recall_at_1 = round(literal_hits_1 / literal_count, 4) if literal_count else 0.0
    recall_at_3 = round(literal_hits_3 / literal_count, 4) if literal_count else 0.0
    recall_at_5 = round(literal_hits_5 / literal_count, 4) if literal_count else 0.0
    mrr = round(literal_rr_sum / literal_count, 4) if literal_count else 0.0
    ndcg_at_5 = round(literal_ndcg_sum / literal_count, 4) if literal_count else 0.0

    avg_ms = round(sum(latencies) / len(latencies), 4) if latencies else 0.0
    p95_ms = round(compute_percentile(latencies, 95), 4)
    total_ms = round(sum(latencies), 4)

    git_sha = get_git_sha()
    iso_now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    cpu_info = platform.processor() or f"{os.cpu_count() or 4}-Core CPU"

    stable_per_case = [
        {"id": r["id"], "rank": r["rank"], "hit": r["hit"]}
        for r in per_case_results
    ]

    corpus_section = {
        "name": "default",
        "documents": doc_count,
        "chunks": total_chunks
    }

    stable_section = {
        "recall_at_1": recall_at_1,
        "recall_at_3": recall_at_3,
        "recall_at_5": recall_at_5,
        "mrr": mrr,
        "ndcg_at_5": ndcg_at_5,
        "per_case": stable_per_case
    }

    full_result = {
        "generated_at": iso_now,
        "git_sha": git_sha,
        "env": {
            "os": f"{platform.system()} {platform.release()}",
            "jdk": get_jdk_version(),
            "pg": "pgvector/pgvector:pg16",
            "redis": "redis:7-alpine",
            "cpu": cpu_info,
            "memory_gb": get_system_memory_gb()
        },
        "corpus": corpus_section,
        "stable": stable_section,
        "timings": {
            "avg_ms": avg_ms,
            "p95_ms": p95_ms,
            "total_ms": total_ms
        },
        "details": per_case_results
    }

    stable_only = {
        "corpus": corpus_section,
        "stable": stable_section
    }

    return full_result, stable_only

def main():
    parser = argparse.ArgumentParser(description="AgentForge Benchmark 生成器")
    parser.add_argument("--base-url", default="http://localhost:8080/api", help="后端 API 基地址")
    parser.add_argument("--dataset-id", type=int, default=1, help="数据集 ID")
    parser.add_argument("--out", help="输出全量 benchmark JSON 路径")
    parser.add_argument("--stable-out", help="输出稳定段 benchmark JSON 路径 (用于防漂移基线)")
    parser.add_argument("--force-upload", action="store_true", help="强制上传基准语料")
    args = parser.parse_args()

    base_url = args.base_url.rstrip('/')

    print(f"[Benchmark] 启动基准测试: base_url={base_url}, dataset_id={args.dataset_id}...")
    try:
        full_result, stable_only = run_benchmark(base_url, args.dataset_id, args.force_upload)
    except Exception as e:
        print(f"[FAIL] 基准测试执行异常: {e}", file=sys.stderr)
        sys.exit(1)

    s = full_result["stable"]
    t = full_result["timings"]
    c = full_result["corpus"]
    print(f"[Benchmark] 执行成功: Chunks={c['chunks']}, Recall@5={s['recall_at_5']:.1%}, MRR={s['mrr']:.4f}, NDCG@5={s['ndcg_at_5']:.4f}, Avg={t['avg_ms']:.2f}ms")

    if args.stable_out:
        out_dir = os.path.dirname(os.path.abspath(args.stable_out))
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
        with open(args.stable_out, "w", encoding="utf-8") as f:
            json.dump(stable_only, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(f"[Benchmark] 已写入稳定段基准: {args.stable_out}")

    if args.out:
        out_dir = os.path.dirname(os.path.abspath(args.out))
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(full_result, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(f"[Benchmark] 已写入全量指标快照: {args.out}")

if __name__ == "__main__":
    main()
