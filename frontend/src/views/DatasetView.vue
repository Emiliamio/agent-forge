<template>
  <div class="p-8 max-w-7xl mx-auto space-y-8">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-white tracking-tight">知识库管理 (Hybrid RAG)</h2>
        <p class="text-sm text-slate-400 mt-1">支持多格式解析、自然语言语义滑动切片、pgvector 稠密与 BM25 稀疏三路混合检索</p>
      </div>
      <button @click="showCreateModal = true" class="px-4 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-sm font-medium transition-all shadow-md shadow-brand-600/20 flex items-center gap-2">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/></svg>
        新建知识库
      </button>
    </div>

    <!-- Main Content Tabs -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- Left: Dataset List & Documents -->
      <div class="lg:col-span-2 space-y-6">
        <!-- Datasets Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div
            v-for="ds in datasets"
            :key="ds.id"
            @click="selectedDataset = ds"
            class="p-5 rounded-2xl border cursor-pointer transition-all"
            :class="selectedDataset?.id === ds.id ? 'bg-dark-card border-brand-500 shadow-lg shadow-brand-500/10' : 'bg-slate-900/40 border-dark-border hover:border-slate-700'"
          >
            <div class="flex items-start justify-between">
              <div class="w-10 h-10 rounded-xl bg-brand-500/10 text-brand-400 flex items-center justify-center font-bold">
                {{ ds.name.substring(0, 1) }}
              </div>
              <span class="text-[11px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                {{ ds.embeddingModel }}
              </span>
            </div>
            <h3 class="text-base font-bold text-white mt-3">{{ ds.name }}</h3>
            <p class="text-xs text-slate-400 mt-1 line-clamp-2">{{ ds.description }}</p>
            <div class="mt-4 pt-3 border-t border-dark-border flex justify-between text-xs text-slate-500 font-mono">
              <span>文档数: {{ ds.docCount }}</span>
              <span>分块数: {{ ds.chunkCount }}</span>
            </div>
          </div>
        </div>

        <!-- Document Upload & List -->
        <div class="p-6 rounded-2xl bg-dark-card border border-dark-border space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-base font-bold text-white flex items-center gap-2">
              <span class="w-2 h-2 rounded-full bg-brand-400"></span>
              【{{ selectedDataset?.name || '请选择知识库' }}】文档列表
            </h3>
            <label class="cursor-pointer px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium border border-dark-border transition-colors flex items-center gap-1.5">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"/></svg>
              上传文件 (PDF/DOCX/MD/TXT)
              <input type="file" class="hidden" @change="handleFileUpload" />
            </label>
          </div>

          <!-- Document Table -->
          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs">
              <thead class="bg-slate-900/60 text-slate-400 font-semibold uppercase tracking-wider border-b border-dark-border">
                <tr>
                  <th class="py-3 px-4">文档名称</th>
                  <th class="py-3 px-4">格式</th>
                  <th class="py-3 px-4">分块数</th>
                  <th class="py-3 px-4">解析状态</th>
                  <th class="py-3 px-4">创建时间</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-dark-border text-slate-300">
                <tr v-for="doc in documents" :key="doc.id" class="hover:bg-slate-800/40 transition-colors">
                  <td class="py-3.5 px-4 font-medium text-white flex items-center gap-2">
                    <svg class="w-4 h-4 text-brand-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>
                    {{ doc.title }}
                  </td>
                  <td class="py-3.5 px-4"><span class="px-2 py-0.5 rounded bg-slate-800 font-mono text-[10px]">{{ doc.fileType }}</span></td>
                  <td class="py-3.5 px-4 font-mono">{{ doc.chunkCount }}</td>
                  <td class="py-3.5 px-4">
                    <span class="inline-flex items-center gap-1 text-emerald-400 font-medium">
                      <span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
                      {{ doc.status }}
                    </span>
                  </td>
                  <td class="py-3.5 px-4 text-slate-500 font-mono">{{ doc.createTime }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- Right: Hybrid Search Testing Sandbox -->
      <div class="space-y-6">
        <div class="p-6 rounded-2xl bg-dark-card border border-dark-border space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-base font-bold text-white flex items-center gap-2">
              <svg class="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
              三路混合检索沙盒
            </h3>
            <span class="text-[10px] text-slate-400 font-mono">Dense + Sparse + RRF</span>
          </div>

          <div>
            <label class="block text-xs font-medium text-slate-400 mb-1.5">输入测试检索语句</label>
            <div class="flex gap-2">
              <input
                v-model="searchQuery"
                type="text"
                placeholder="例如：多租户数据隔离机制是什么？"
                class="flex-1 px-3.5 py-2 bg-slate-900 border border-dark-border rounded-xl text-xs text-slate-200 outline-none focus:border-brand-500 transition-colors"
                @keyup.enter="runSearch"
              />
              <button
                @click="runSearch"
                :disabled="searching"
                class="px-4 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-xs font-medium transition-colors"
              >
                {{ searching ? '检索中...' : '测试检索' }}
              </button>
            </div>
          </div>

          <!-- Search Results Stream -->
          <div class="space-y-3 pt-2">
            <div v-if="searchResults.length === 0" class="p-8 text-center text-slate-500 text-xs font-mono">
              输入问题即可查看 pgvector + BM25 融合召回切片与溯源打分
            </div>
            <div
              v-for="(chunk, idx) in searchResults"
              :key="idx"
              class="p-3.5 rounded-xl bg-slate-900/80 border border-dark-border space-y-2 text-xs"
            >
              <div class="flex items-center justify-between">
                <span class="font-bold text-brand-400">#{{ idx + 1 }} 候选切片</span>
                <span class="px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 font-mono text-[10px]">
                  RRF 综合分: {{ chunk.score.toFixed(4) }}
                </span>
              </div>
              <p class="text-slate-300 leading-relaxed text-[11px] bg-slate-950/60 p-2.5 rounded-lg border border-slate-800/80">
                {{ chunk.content }}
              </p>
              <div class="flex justify-between text-[10px] text-slate-500 font-mono pt-1">
                <span>稠密排名: {{ chunk.denseRank }}</span>
                <span>BM25排名: {{ chunk.sparseRank }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const showCreateModal = ref(false)
const searching = ref(false)
const searchQuery = ref('多租户数据隔离机制与 JsqlParser AST 拦截原理')

const datasets = ref([
  { id: 1, name: '企业通用制度知识库', description: '包含公司多租户安全规范、技术架构手册与运维 SOP。', embeddingModel: 'text-embedding-3-small', docCount: 4, chunkCount: 128 },
  { id: 2, name: '金融合规与财务报表', description: '高精度财务跨页报表、风控规章与合规审计指南。', embeddingModel: 'text-embedding-3-small', docCount: 2, chunkCount: 64 }
])

const selectedDataset = ref(datasets.value[0])

const documents = ref([
  { id: 101, title: 'AgentForge 架构白皮书.md', fileType: 'MARKDOWN', chunkCount: 42, status: '解析就绪', createTime: '2026-08-28 10:15' },
  { id: 102, title: '多租户安全与隔离规范.pdf', fileType: 'PDF', chunkCount: 56, status: '解析就绪', createTime: '2026-08-28 10:20' },
  { id: 103, title: 'pgvector 混合检索调优.docx', fileType: 'DOCX', chunkCount: 30, status: '解析就绪', createTime: '2026-08-28 10:35' }
])

const searchResults = ref([
  {
    content: '通过 MyBatis-Plus TenantLineInnerInterceptor 插件，在 JsqlParser SQL AST 语法树层级自动追加 AND tenant_id = ?，彻底杜绝数据越权。',
    score: 0.9425,
    denseRank: 1,
    sparseRank: 1
  },
  {
    content: '混合检索采用 RRF (Reciprocal Rank Fusion) 倒数排名融合算法无量纲整合 pgvector HNSW 稠密余弦距离与 PostgreSQL BM25 全文检索。',
    score: 0.8872,
    denseRank: 2,
    sparseRank: 3
  }
])

const runSearch = () => {
  if (!searchQuery.value.trim()) return
  searching.value = true
  setTimeout(() => {
    searching.value = false
  }, 400)
}

const handleFileUpload = () => {
  alert('文档已加入异步分片流水线，后台正基于 Virtual Threads 提取并向量化！')
}
</script>
