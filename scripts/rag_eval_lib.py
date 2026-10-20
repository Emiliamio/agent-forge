#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AgentForge RAG 真实 HTTP 评测基础公共库 (scripts/rag_eval_lib.py)
供 evaluate-cjk-rag.py 与 benchmark.py 统一调用，确保地面真值判定与指标计算完全同构。
"""

import os
import sys
import json
import time
import math
import urllib.request
import urllib.error

# 20 组真实业务提问与基准预期目标
BENCHMARK_CASES = [
    # 组 1: 字面覆盖自然问句 (1~18 题，纳入 Recall 与 MRR 硬门禁考核)
    {
        "id": 1,
        "query": "北京出差住宿报销标准是多少",
        "target_section": "一线城市差旅住宿标准",
        "expected_keywords": ["北京", "住宿", "800"],
        "category": "差旅报销",
        "is_literal": True
    },
    {
        "id": 2,
        "query": "员工出差交通补助每天按什么标准发",
        "target_section": "交通包干与市内出行补贴",
        "expected_keywords": ["交通", "包干", "100"],
        "category": "差旅报销",
        "is_literal": True
    },
    {
        "id": 3,
        "query": "公司发票抬头和税号怎么开具",
        "target_section": "企业增值税发票开具与税号规范",
        "expected_keywords": ["发票", "抬头", "纳税人识别号"],
        "category": "财务合规",
        "is_literal": True
    },
    {
        "id": 4,
        "query": "商务宴请餐饮发票报销需要哪些凭证",
        "target_section": "商务接待宴请发票报销要件",
        "expected_keywords": ["餐饮", "发票", "水单"],
        "category": "财务合规",
        "is_literal": True
    },
    {
        "id": 5,
        "query": "带薪年休假天数根据什么工龄计算",
        "target_section": "法定带薪年休假计算规则",
        "expected_keywords": ["年休假", "工龄", "司龄"],
        "category": "行政考勤",
        "is_literal": True
    },
    {
        "id": 6,
        "query": "周末交付加班给调休还是加班费",
        "target_section": "加班补偿与加班费核算基准",
        "expected_keywords": ["加班", "调休", "300%"],
        "category": "行政考勤",
        "is_literal": True
    },
    {
        "id": 7,
        "query": "请病假超过两天需要提供什么证明",
        "target_section": "病假证明材料审核与病假工资",
        "expected_keywords": ["病假", "医院", "证明"],
        "category": "行政考勤",
        "is_literal": True
    },
    {
        "id": 8,
        "query": "涉及十万元以上的商业合同怎么审批",
        "target_section": "大额商业合同联合审批盖章",
        "expected_keywords": ["合同", "十万", "审批"],
        "category": "法务合同",
        "is_literal": True
    },
    {
        "id": 9,
        "query": "采购招标为什么必须至少三家比价",
        "target_section": "采购招标三家比价采购机制",
        "expected_keywords": ["采购", "比价", "三家"],
        "category": "供应链采购",
        "is_literal": True
    },
    {
        "id": 10,
        "query": "研发岗新员工申领笔记本电脑配置要求",
        "target_section": "研发新员工笔记本电脑配置申领",
        "expected_keywords": ["笔记本", "32GB", "开发"],
        "category": "IT与办公",
        "is_literal": True
    },
    {
        "id": 11,
        "query": "远程办公接入内网 SSL VPN 账号申请",
        "target_section": "远程办公内网 SSL VPN 与动态双因子接入",
        "expected_keywords": ["VPN", "远程", "OA"],
        "category": "IT与办公",
        "is_literal": True
    },
    {
        "id": 12,
        "query": "离职交接手续需要经过哪些部门核清",
        "target_section": "离职申请时限与业务交接清单",
        "expected_keywords": ["离职", "交接", "资产"],
        "category": "人事调动",
        "is_literal": True
    },
    {
        "id": 13,
        "query": "员工试用期转正考核合格标准是几分",
        "target_section": "试用期转正考核标准与述职答辩",
        "expected_keywords": ["试用期", "转正", "80"],
        "category": "人事调动",
        "is_literal": True
    },
    {
        "id": 14,
        "query": "因公临时借款的还款冲账期限是几天",
        "target_section": "员工因公临时借款与冲账时限",
        "expected_keywords": ["借款", "冲账", "30"],
        "category": "财务合规",
        "is_literal": True
    },
    {
        "id": 15,
        "query": "多租户行级物理数据隔离机制怎么实现的",
        "target_section": "多租户行级物理数据隔离机制",
        "expected_keywords": ["租户", "隔离", "TenantLine"],
        "category": "技术中台",
        "is_literal": True
    },
    {
        "id": 16,
        "query": "混合检索 RRF 算法和 pgvector 向量索引",
        "target_section": "混合检索 RRF 算法和 pgvector 向量索引",
        "expected_keywords": ["混合检索", "RRF", "pgvector"],
        "category": "技术中台",
        "is_literal": True
    },
    {
        "id": 17,
        "query": "API 接口防暴力破解与令牌桶限流规则",
        "target_section": "API 接口防暴力破解与令牌桶限流规则",
        "expected_keywords": ["限流", "防爆破", "令牌桶"],
        "category": "技术中台",
        "is_literal": True
    },
    {
        "id": 18,
        "query": "敏感数据 PII 手机号和身份证脱敏掩码",
        "target_section": "敏感数据 PII 手机号和身份证脱敏掩码",
        "expected_keywords": ["PII", "脱敏", "身份证"],
        "category": "技术中台",
        "is_literal": True
    },
    # 组 2: 纯同义词改写边界 (19~20 题，单列独立评估，不隐瞒架构边界)
    {
        "id": 19,
        "query": "酒店能报多少钱",  # 典型纯同义改写边界 (原制度使用"住宿")
        "target_section": "一线城市差旅住宿标准",
        "expected_keywords": ["住宿", "800"],
        "category": "同义词边界",
        "is_literal": False
    },
    {
        "id": 20,
        "query": "打车发票能不能直接报销",  # 典型同义改写边界 (原制度使用"交通包干")
        "target_section": "交通包干与市内出行补贴",
        "expected_keywords": ["交通", "包干"],
        "category": "同义词边界",
        "is_literal": False
    }
]

def make_http_request(url, method="GET", headers=None, data=None, timeout=15):
    """通用 HTTP 请求助手"""
    if headers is None:
        headers = {}
    req = urllib.request.Request(url, method=method, headers=headers)
    if data is not None:
        if isinstance(data, (dict, list)):
            data_bytes = json.dumps(data, ensure_ascii=False).encode('utf-8')
            req.add_header('Content-Type', 'application/json; charset=utf-8')
        elif isinstance(data, bytes):
            data_bytes = data
        else:
            data_bytes = str(data).encode('utf-8')
        req.data = data_bytes
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode('utf-8')
            return resp.status, json.loads(body)
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"code": e.code, "message": body}
    except Exception as e:
        return 0, {"code": 500, "message": str(e)}

def check_server_health(base_url):
    """探活 GET /system/health"""
    status, health = make_http_request(f"{base_url}/system/health")
    ok = (status == 200 and health.get("code") == 200)
    return ok, status, health

def login_and_get_token(base_url, username="admin", password="admin123456", tenant_code="tenant_default"):
    """管理员登录获取 Bearer Token"""
    login_data = {
        "tenantCode": tenant_code,
        "username": username,
        "password": password
    }
    status, res = make_http_request(f"{base_url}/auth/login", method="POST", data=login_data)
    if status == 200 and res.get("code") == 200 and "data" in res:
        return res["data"].get("token")
    return None

def upload_benchmark_file(base_url, token, dataset_id, filepath):
    """通过标准 Multipart/form-data 上传企业基准知识库文档"""
    boundary = '----AgentForgeBenchmarkBoundary' + str(int(time.time()))
    filename = os.path.basename(filepath)
    with open(filepath, 'rb') as f:
        file_bytes = f.read()

    body = bytearray()
    body.extend(f'--{boundary}\r\n'.encode('utf-8'))
    body.extend(f'Content-Disposition: form-data; name="datasetId"\r\n\r\n{dataset_id}\r\n'.encode('utf-8'))
    body.extend(f'--{boundary}\r\n'.encode('utf-8'))
    body.extend(f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode('utf-8'))
    body.extend('Content-Type: text/markdown\r\n\r\n'.encode('utf-8'))
    body.extend(file_bytes)
    body.extend(b'\r\n')
    body.extend(f'--{boundary}--\r\n'.encode('utf-8'))

    url = f"{base_url}/documents/upload"
    headers = {
        'Authorization': f'Bearer {token}',
        'Content-Type': f'multipart/form-data; boundary={boundary}'
    }
    return make_http_request(url, method="POST", headers=headers, data=bytes(body))

def fetch_dataset_chunks(base_url, auth_headers, dataset_id):
    """
    拉取数据集下全部分片的 (chunkId, title, content)，用于构建 chunkId 级地面真值。
    数据来源为真实接口 GET /documents/list + GET /rag/chunks/document/{id}。
    """
    chunks = []
    status, res = make_http_request(f"{base_url}/documents/list?datasetId={dataset_id}&pageNum=1&pageSize=100", headers=auth_headers)
    if status != 200 or res.get("code") != 200:
        return chunks

    data_obj = res.get("data") or {}
    documents = data_obj.get("list") or data_obj.get("records") or []
    for doc in documents:
        if doc.get("parseStatus") != "SUCCESS":
            continue
        doc_id = doc.get("id")
        if not doc_id:
            continue
        page = 1
        while True:
            s, pres = make_http_request(
                f"{base_url}/rag/chunks/document/{doc_id}?page={page}&size=100",
                headers=auth_headers
            )
            if s != 200 or pres.get("code") != 200:
                break
            pdata = pres.get("data") or {}
            records = pdata.get("records") or pdata.get("list") or []
            for rec in records:
                title = ""
                try:
                    meta = json.loads(rec.get("metadata") or "{}")
                    title = meta.get("title") or ""
                except Exception:
                    title = ""
                chunks.append({
                    "id": rec.get("id"),
                    "title": title,
                    "content": rec.get("content") or ""
                })
            total_pages = int(pdata.get("pages") or 1)
            if page >= total_pages or not records:
                break
            page += 1
    return chunks

def resolve_ground_truth(cases, chunks):
    """
    把每条用例的 target_section 解析成真实切片 ID 列表（地面真值）。
    优先按切片元数据 title 精确包含匹配，其次退回正文包含匹配；都解析不到则回退为空。
    """
    ground_truth = {}
    for case in cases:
        target = case.get("target_section") or ""
        ids = []
        if target:
            ids = [c["id"] for c in chunks if target in (c["title"] or "")]
            if not ids:
                ids = [c["id"] for c in chunks if target in (c["content"] or "")]
        ground_truth[case["id"]] = [cid for cid in ids if cid is not None]
    return ground_truth

def prepare_benchmark_corpus(base_url, auth_headers, dataset_id=1, filepath=None, force_upload=False):
    """
    检查知识库切片数是否 >= 50；若不足则上传基准语料并等待解析完成。
    返回当前有效切片数 total_chunks 与文档数 doc_count。
    """
    if filepath is None:
        filepath = os.path.join(os.path.dirname(__file__), "..", "docs", "benchmark_enterprise_policy.md")
        filepath = os.path.abspath(filepath)

    status, doc_list_res = make_http_request(f"{base_url}/documents/list?datasetId={dataset_id}", headers=auth_headers)
    total_chunks = 0
    doc_count = 0
    needs_upload = force_upload

    if status == 200 and doc_list_res.get("code") == 200:
        data_obj = doc_list_res.get("data", {})
        records = data_obj.get("list") or data_obj.get("records") or []
        doc_count = len(records)
        total_chunks = sum(r.get("chunkCount", 0) for r in records if r.get("parseStatus") == "SUCCESS")
        if total_chunks < 50:
            needs_upload = True

    if needs_upload and os.path.exists(filepath):
        token = auth_headers.get("Authorization", "").replace("Bearer ", "")
        up_status, up_res = upload_benchmark_file(base_url, token, dataset_id, filepath)
        if up_status == 200 and up_res.get("code") == 200:
            for _ in range(15):
                time.sleep(1)
                s, check = make_http_request(f"{base_url}/documents/list?datasetId={dataset_id}", headers=auth_headers)
                if s == 200:
                    c_data = check.get("data", {})
                    recs = c_data.get("list") or c_data.get("records") or []
                    doc_count = len(recs)
                    total_chunks = sum(r.get("chunkCount", 0) for r in recs if r.get("parseStatus") == "SUCCESS")
                    if total_chunks >= 50:
                        break

    return total_chunks, doc_count

def execute_rag_search(base_url, auth_headers, dataset_id, query, top_k=5, min_score=0.0):
    """执行单次真实 HTTP POST /rag/search"""
    search_payload = {
        "datasetIds": [dataset_id],
        "query": query,
        "topK": top_k,
        "minScore": min_score
    }
    t0 = time.time()
    s_code, s_res = make_http_request(
        f"{base_url}/rag/search",
        method="POST",
        headers=auth_headers,
        data=search_payload
    )
    latency_ms = (time.time() - t0) * 1000
    citations = []
    if s_code == 200 and s_res.get("code") == 200:
        citations = s_res.get("data", []) or []
    return s_code, citations, latency_ms

def evaluate_case(base_url, auth_headers, dataset_id, case, ground_truth, top_k=5):
    """评测单条用例并判断命中等级"""
    query = case["query"]
    expected = case["expected_keywords"]
    target = case["target_section"]

    s_code, citations, latency_ms = execute_rag_search(
        base_url, auth_headers, dataset_id, query, top_k=top_k, min_score=0.0
    )

    top_score = 0.0
    hit_rank = 0
    judged_by_chunk_id = False

    if citations:
        top_score = citations[0].get("score", 0.0)
        gt_ids = ground_truth.get(case["id"]) or []
        if gt_ids:
            judged_by_chunk_id = True
            for idx, cit in enumerate(citations):
                if cit.get("chunkId") in gt_ids:
                    hit_rank = idx + 1
                    break
        else:
            for idx, cit in enumerate(citations):
                section_title = cit.get("sectionTitle", "")
                snippet = cit.get("snippet", "")
                has_section_match = target and (target in section_title)
                matched_words = [kw for kw in expected if kw in snippet or kw in section_title]
                has_keyword_match = len(matched_words) >= 2 or (len(expected) <= 2 and len(matched_words) >= 1)
                if has_section_match or has_keyword_match:
                    hit_rank = idx + 1
                    break

    hit = (hit_rank > 0)
    return {
        "id": case["id"],
        "category": case["category"],
        "query": query,
        "is_literal": case["is_literal"],
        "hit": hit,
        "rank": hit_rank,
        "latency_ms": latency_ms,
        "top_score": top_score,
        "judged_by_chunk_id": judged_by_chunk_id,
        "citations_count": len(citations)
    }

def calculate_ndcg_at_k(hit_rank, k=5):
    """计算单个问句的 NDCG@K (单目标真值，理想 rank 为 1)"""
    if hit_rank is None or hit_rank <= 0 or hit_rank > k:
        return 0.0
    dcg = 1.0 / math.log2(hit_rank + 1)
    idcg = 1.0 / math.log2(1 + 1)  # 1.0
    return dcg / idcg
