"""
AgentForge 1000 QPS 高并发压测脚本 (Locust)
用于模拟千级企业并发用户对混合 RAG 检索、DAG 响应式工作流与 ReAct 智能体进行压力测试
"""
import random
from locust import HttpUser, task, between

SAMPLE_QUERIES = [
    "多租户数据隔离机制与 JsqlParser AST 拦截原理",
    "pgvector HNSW 稠密检索与 BM25 稀疏检索的 RRF 融合权重",
    "DAG 工作流响应式并发调度分层算法是什么",
    "财务跨页报表表头级联下沉算法",
    "Redis 向量语义降本缓存如何降低 60% API 费用"
]

class AgentForgeLoadTestUser(HttpUser):
    wait_time = between(0.1, 0.5)
    headers = {
        "X-Tenant-Id": "1",
        "Content-Type": "application/json"
    }

    @task(4)
    def test_hybrid_search(self):
        """压测三路混合检索 (Dense + Sparse + RRF)"""
        payload = {
            "query": random.choice(SAMPLE_QUERIES),
            "topK": 5,
            "minScore": 0.45,
            "datasetIds": [1]
        }
        with self.client.post("/api/rag/search", json=payload, headers=self.headers, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"RAG 检索失败: HTTP {response.status_code}")

    @task(3)
    def test_semantic_cache_billing(self):
        """压测 Redis 语义缓存命中率与 Token 审计统计"""
        with self.client.get("/api/billing/stats", headers=self.headers, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"计费统计拉取失败: HTTP {response.status_code}")

    @task(2)
    def test_dag_workflow_execution(self):
        """压测 DAG 响应式工作流调度"""
        payload = {
            "inputs": {
                "query": random.choice(SAMPLE_QUERIES)
            }
        }
        with self.client.post("/api/workflows/1/run", json=payload, headers=self.headers, catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"工作流执行失败: HTTP {response.status_code}")
