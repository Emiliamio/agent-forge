<template>
  <div class="p-8 max-w-7xl mx-auto space-y-8">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-white tracking-tight">
          知识库管理 (Hybrid RAG)
        </h2>
        <p class="text-sm text-slate-400 mt-1">
          支持多格式解析、自然语言语义滑动切片、pgvector 稠密与 BM25 稀疏三路混合检索
        </p>
      </div>
      <button
        class="px-4 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-sm font-medium transition-all shadow-md shadow-brand-600/20 flex items-center gap-2"
        @click="showCreateModal = true"
      >
        <svg
          class="w-4 h-4"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        ><path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M12 4v16m8-8H4"
        /></svg>
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
            class="p-5 rounded-2xl border cursor-pointer transition-all"
            :class="selectedDataset?.id === ds.id ? 'bg-dark-card border-brand-500 shadow-lg shadow-brand-500/10' : 'bg-slate-900/40 border-dark-border hover:border-slate-700'"
            @click="selectDataset(ds)"
          >
            <div class="flex items-start justify-between">
              <div class="w-10 h-10 rounded-xl bg-brand-500/10 text-brand-400 flex items-center justify-center font-bold">
                {{ ds.name.substring(0, 1) }}
              </div>
              <span class="text-[11px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono">
                {{ ds.embeddingModel || 'text-embedding-3-small' }}
              </span>
            </div>
            <h3 class="text-base font-bold text-white mt-3 truncate">
              {{ ds.name }}
            </h3>
            <p class="text-xs text-slate-400 mt-1 line-clamp-2">
              {{ ds.description || '暂无描述' }}
            </p>
            <div class="mt-4 pt-3 border-t border-dark-border flex justify-between text-xs text-slate-500 font-mono">
              <span>分块大小: {{ ds.chunkSize || 500 }}</span>
              <span>重叠: {{ ds.chunkOverlap || 50 }}</span>
            </div>
          </div>
        </div>

        <!-- Document Upload & List -->
        <div class="p-6 rounded-2xl bg-dark-card border border-dark-border space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="text-base font-bold text-white flex items-center gap-2">
              <span class="w-2 h-2 rounded-full bg-brand-400" />
              【{{ selectedDataset?.name || '请选择知识库' }}】文档列表
            </h3>
            <label class="cursor-pointer px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium border border-dark-border transition-colors flex items-center gap-1.5">
              <svg
                class="w-3.5 h-3.5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              ><path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"
              /></svg>
              <span>{{ uploading ? '上传解析中...' : '上传文件 (PDF/DOCX/MD/TXT)' }}</span>
              <input
                type="file"
                class="hidden"
                :disabled="uploading"
                @change="handleFileUpload"
              >
            </label>
          </div>

          <!-- Document Table -->
          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs">
              <thead class="bg-slate-900/60 text-slate-400 font-semibold uppercase tracking-wider border-b border-dark-border">
                <tr>
                  <th class="py-3 px-4">
                    文档名称
                  </th>
                  <th class="py-3 px-4">
                    格式
                  </th>
                  <th class="py-3 px-4">
                    分块数
                  </th>
                  <th class="py-3 px-4">
                    解析状态
                  </th>
                  <th class="py-3 px-4">
                    创建时间
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-dark-border text-slate-300">
                <tr v-if="documents.length === 0">
                  <td
                    colspan="5"
                    class="py-8 text-center text-slate-500 font-mono"
                  >
                    当前知识库暂无文档，请点击右上角上传文件进行向量切片
                  </td>
                </tr>
                <tr
                  v-for="doc in documents"
                  :key="doc.id"
                  class="hover:bg-slate-800/40 transition-colors"
                >
                  <td class="py-3.5 px-4 font-medium text-white flex items-center gap-2">
                    <svg
                      class="w-4 h-4 text-brand-400"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    ><path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                    /></svg>
                    {{ doc.fileName }}
                  </td>
                  <td class="py-3.5 px-4">
                    <span class="px-2 py-0.5 rounded bg-slate-800 font-mono text-[10px]">{{ doc.fileType }}</span>
                  </td>
                  <td class="py-3.5 px-4 font-mono">
                    {{ doc.chunkCount || 0 }}
                  </td>
                  <td class="py-3.5 px-4">
                    <span
                      class="inline-flex items-center gap-1 font-medium text-[11px]"
                      :class="doc.parseStatus === 'SUCCESS' ? 'text-emerald-400' : (doc.parseStatus === 'FAILED' ? 'text-rose-400' : 'text-amber-400')"
                    >
                      <span
                        class="w-1.5 h-1.5 rounded-full"
                        :class="doc.parseStatus === 'SUCCESS' ? 'bg-emerald-400' : 'bg-amber-400'"
                      />
                      {{ doc.parseStatus }}
                    </span>
                  </td>
                  <td class="py-3.5 px-4 text-slate-500 font-mono">
                    {{ doc.createdAt ? doc.createdAt.replace('T', ' ').substring(0, 16) : '-' }}
                  </td>
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
              <svg
                class="w-4 h-4 text-emerald-400"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              ><path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
              /></svg>
              三路混合检索沙盒
            </h3>
            <span class="text-[10px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono">
              HNSW + BM25 + RRF
            </span>
          </div>

          <p class="text-xs text-slate-400">
            输入自然语言测试混合召回，后端真实执行 PostgreSQL pgvector 与 tsvector 倒数排名融合：
          </p>

          <div class="space-y-3">
            <textarea
              v-model="searchQuery"
              rows="3"
              class="w-full px-3.5 py-2.5 bg-slate-900 border border-dark-border rounded-xl text-xs text-white placeholder-slate-500 focus:border-brand-500 outline-none resize-none"
              placeholder="请输入检索关键词或问题..."
            />
            <button
              :disabled="searching || !selectedDataset"
              class="w-full py-2.5 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-xs font-semibold shadow-md shadow-brand-600/20 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
              @click="runSearch"
            >
              <svg
                v-if="searching"
                class="animate-spin w-3.5 h-3.5 text-white"
                fill="none"
                viewBox="0 0 24 24"
              ><circle
                class="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                stroke-width="4"
              /><path
                class="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8v8H4z"
              /></svg>
              <span>{{ searching ? '混合检索执行中...' : '执行三路检索召回' }}</span>
            </button>
          </div>

          <!-- Search Results List -->
          <div class="space-y-3 pt-2">
            <div class="text-xs font-semibold text-slate-400 flex items-center justify-between">
              <span>检索命中片段 (Top-K)</span>
              <span class="text-[10px] font-mono text-slate-500">共 {{ searchResults.length }} 条引用</span>
            </div>

            <div
              v-if="searchResults.length === 0"
              class="p-6 text-center text-slate-500 text-xs border border-dashed border-dark-border rounded-xl"
            >
              暂无召回结果，请输入关键词并发起检索
            </div>

            <div
              v-for="(chunk, idx) in searchResults"
              :key="idx"
              class="p-3.5 rounded-xl bg-slate-900/60 border border-dark-border space-y-2 hover:border-slate-700 transition-colors"
            >
              <div class="flex items-center justify-between text-xs">
                <span class="font-bold text-white flex items-center gap-1.5 truncate">
                  <span class="w-1.5 h-1.5 rounded-full bg-brand-400" />
                  {{ chunk.documentName || chunk.title || `切片 #${idx + 1}` }}
                </span>
                <span class="text-[11px] font-mono text-emerald-400 font-bold">
                  得分: {{ (chunk.score || 0).toFixed(4) }}
                </span>
              </div>
              <p class="text-slate-300 leading-relaxed text-[11px] bg-slate-950/60 p-2.5 rounded-lg border border-slate-800/80">
                {{ chunk.content || chunk.snippet }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create Dataset Modal -->
    <div
      v-if="showCreateModal"
      class="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4"
    >
      <div class="bg-dark-card border border-dark-border rounded-2xl w-full max-w-md p-6 space-y-5 shadow-2xl">
        <h3 class="text-lg font-bold text-white">
          新建企业知识库数据集
        </h3>
        <div class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-slate-400 mb-1.5">知识库名称</label>
            <input
              v-model="newDataset.name"
              class="w-full px-3.5 py-2.5 bg-slate-900 border border-dark-border rounded-xl text-xs text-white focus:border-brand-500 outline-none"
              placeholder="例如：财务审计规范知识库"
            >
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-400 mb-1.5">知识库描述</label>
            <textarea
              v-model="newDataset.description"
              rows="3"
              class="w-full px-3.5 py-2.5 bg-slate-900 border border-dark-border rounded-xl text-xs text-white focus:border-brand-500 outline-none resize-none"
              placeholder="简要说明包含的制度或技术文档范围..."
            />
          </div>
        </div>
        <div class="flex justify-end gap-3 pt-2">
          <button
            class="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-xl transition-colors"
            @click="showCreateModal = false"
          >
            取消
          </button>
          <button
            class="px-4 py-2 bg-brand-600 hover:bg-brand-500 text-white text-xs font-semibold rounded-xl shadow-md transition-all"
            @click="createDataset"
          >
            确认创建
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import client from '@/api/client'

const showCreateModal = ref(false)
const searching = ref(false)
const uploading = ref(false)
const searchQuery = ref('多租户数据隔离机制与 JsqlParser AST 拦截原理')

const datasets = ref([])
const selectedDataset = ref(null)
const documents = ref([])
const searchResults = ref([])

const newDataset = ref({
  name: '',
  description: '',
  embeddingModel: 'text-embedding-3-small',
  embeddingDim: 1536,
  chunkSize: 500,
  chunkOverlap: 50
})

const fetchDatasets = async () => {
  try {
    const res = await client.get('/datasets?pageNum=1&pageSize=20')
    if (res && res.records) {
      datasets.value = res.records
      if (datasets.value.length > 0 && !selectedDataset.value) {
        selectDataset(datasets.value[0])
      }
    }
  } catch (e) {
    console.error('获取知识库列表失败:', e)
  }
}

const selectDataset = async (ds) => {
  selectedDataset.value = ds
  fetchDocuments(ds.id)
}

const fetchDocuments = async (datasetId) => {
  try {
    const res = await client.get(`/documents/list?datasetId=${datasetId}&pageNum=1&pageSize=50`)
    if (res && res.records) {
      documents.value = res.records
    } else {
      documents.value = []
    }
  } catch (e) {
    documents.value = []
  }
}

const handleFileUpload = async (event) => {
  const file = event.target.files?.[0]
  if (!file || !selectedDataset.value) return

  const formData = new FormData()
  formData.append('datasetId', selectedDataset.value.id)
  formData.append('file', file)

  uploading.value = true
  try {
    await client.post('/documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    alert('文档上传成功，已触发异步分片与高维向量入库！')
    fetchDocuments(selectedDataset.value.id)
  } catch (e) {
    alert('上传失败: ' + (e.message || '网络异常'))
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

const runSearch = async () => {
  if (!searchQuery.value.trim() || !selectedDataset.value) return
  searching.value = true
  searchResults.value = []

  try {
    const res = await client.post('/rag/search', {
      datasetIds: [selectedDataset.value.id],
      query: searchQuery.value.trim(),
      topK: 5,
      minScore: 0.1
    })
    if (Array.isArray(res)) {
      searchResults.value = res
    }
  } catch (e) {
    alert('混合检索调用失败: ' + (e.message || '未知错误'))
  } finally {
    searching.value = false
  }
}

const createDataset = async () => {
  if (!newDataset.value.name.trim()) {
    alert('请输入知识库名称')
    return
  }
  try {
    await client.post('/datasets', newDataset.value)
    showCreateModal.value = false
    newDataset.value.name = ''
    newDataset.value.description = ''
    await fetchDatasets()
  } catch (e) {
    alert('创建失败: ' + (e.message || '网络异常'))
  }
}

onMounted(() => {
  fetchDatasets()
})
</script>
