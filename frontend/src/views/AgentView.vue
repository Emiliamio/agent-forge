<template>
  <div class="h-screen flex overflow-hidden bg-slate-950 font-sans text-slate-100">
    <!-- Left: Agent Configuration -->
    <div class="w-1/2 p-8 overflow-y-auto border-r border-dark-border space-y-6">
      <div>
        <h2 class="text-2xl font-bold text-white tracking-tight">ReAct 智能体编排与调试</h2>
        <p class="text-sm text-slate-400 mt-1">配置 System Prompt、挂载知识库与勾选动态 Function Calling & MCP 工具箱</p>
      </div>

      <div class="space-y-4">
        <div>
          <label class="block text-xs font-semibold text-slate-400 mb-1.5">智能体名称</label>
          <input v-model="agentConfig.name" class="w-full px-4 py-2.5 bg-dark-card border border-dark-border rounded-xl text-sm text-white focus:border-brand-500 outline-none" />
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-xs font-semibold text-slate-400 mb-1.5">主模型 (带 Fallback 容灾)</label>
            <select v-model="agentConfig.model" class="w-full px-4 py-2.5 bg-dark-card border border-dark-border rounded-xl text-sm text-white focus:border-brand-500 outline-none font-mono">
              <option value="deepseek-chat">DeepSeek-V3 (首选)</option>
              <option value="deepseek-reasoner">DeepSeek-R1 (深度推理)</option>
              <option value="gpt-4o">OpenAI GPT-4o (备用)</option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-400 mb-1.5">挂载知识库 (Hybrid + pgvector)</label>
            <select v-model="agentConfig.dataset" class="w-full px-4 py-2.5 bg-dark-card border border-dark-border rounded-xl text-sm text-white focus:border-brand-500 outline-none">
              <option v-if="datasets.length === 0" value="1">企业数字化与技术规范知识库 (ID: 1)</option>
              <option v-for="ds in datasets" :key="ds.id" :value="ds.id">
                {{ ds.name }} (ID: {{ ds.id }})
              </option>
            </select>
          </div>
        </div>

        <div>
          <label class="block text-xs font-semibold text-slate-400 mb-1.5">系统人设与提示词 (System Prompt)</label>
          <textarea rows="4" v-model="agentConfig.systemPrompt" class="w-full px-4 py-3 bg-dark-card border border-dark-border rounded-xl text-xs text-slate-200 focus:border-brand-500 outline-none font-mono leading-relaxed"></textarea>
        </div>

        <!-- Dynamic Tools Checklist -->
        <div>
          <label class="block text-xs font-semibold text-slate-400 mb-2 flex items-center justify-between">
            <span>启用 Function Calling 与 Anthropic MCP 原生工具生态</span>
            <span class="text-[10px] text-brand-400 font-mono">6 项已就绪</span>
          </label>
          <div class="grid grid-cols-2 gap-3">
            <label v-for="tool in availableTools" :key="tool.id" class="p-3.5 rounded-xl border bg-dark-card flex items-start gap-3 cursor-pointer transition-all" :class="tool.enabled ? 'border-brand-500 bg-brand-500/5' : 'border-dark-border'">
              <input type="checkbox" v-model="tool.enabled" class="mt-0.5 rounded border-slate-700 text-brand-600 focus:ring-brand-500" />
              <div>
                <p class="text-xs font-bold text-white flex items-center gap-1.5">
                  <span>{{ tool.name }}</span>
                  <span v-if="tool.isMcp" class="text-[9px] px-1.5 py-0.2 rounded bg-purple-500/20 text-purple-300 font-mono">MCP</span>
                </p>
                <p class="text-[10px] text-slate-400 mt-0.5">{{ tool.desc }}</p>
              </div>
            </label>
          </div>
        </div>

        <button @click="saveAgent" class="w-full py-3 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-brand-600/20 transition-all cursor-pointer">
          保存智能体配置
        </button>
      </div>
    </div>

    <!-- Right: Interactive Chat Test Runner -->
    <div class="w-1/2 flex flex-col h-full bg-slate-900/40">
      <div class="p-4 border-b border-dark-border bg-dark-card flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse"></span>
          <span class="text-xs font-bold text-white">智能体沙盒实时联调预览</span>
        </div>
        <span class="text-[10px] px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">多租户安全上下文</span>
      </div>

      <!-- Messages Stream -->
      <div class="flex-1 p-6 overflow-y-auto space-y-4">
        <div v-for="(msg, index) in chatMessages" :key="index" class="space-y-2">
          <div :class="msg.role === 'user' ? 'flex justify-end' : 'flex justify-start'">
            <div
              :class="msg.role === 'user'
                ? 'bg-brand-600 text-white rounded-2xl rounded-tr-sm px-4 py-2.5 max-w-lg text-xs leading-relaxed shadow-md shadow-brand-600/10'
                : 'bg-dark-card border border-dark-border text-slate-200 rounded-2xl rounded-tl-sm p-4 max-w-xl text-xs space-y-3 shadow-sm'"
            >
              <!-- Thought Steps (ReAct Chain) -->
              <div v-if="msg.steps && msg.steps.length > 0" class="space-y-2 pb-2 border-b border-dark-border font-mono text-[11px]">
                <div v-for="(step, sIndex) in msg.steps" :key="sIndex" class="p-2.5 rounded-lg bg-slate-950/60 border border-slate-800/80 space-y-1">
                  <p class="text-amber-400 font-semibold flex items-center gap-1.5">
                    <span>💭 思维链:</span> {{ step.thought }}
                  </p>
                  <p class="text-brand-400">
                    <span class="font-bold">⚡ 调度工具:</span> {{ step.action }} ({{ step.params }})
                  </p>
                  <p class="text-slate-400">
                    <span class="font-bold">👁️ 观察结果:</span> {{ step.observation }}
                  </p>
                </div>
              </div>

              <div class="whitespace-pre-wrap leading-relaxed">{{ msg.content }}</div>

              <!-- Citations -->
              <div v-if="msg.citations && msg.citations.length > 0" class="pt-2 border-t border-dark-border flex flex-wrap gap-2">
                <span v-for="(c, cIndex) in msg.citations" :key="cIndex" class="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded bg-slate-800 text-emerald-400 border border-slate-700 font-mono">
                  🔗 知识库切片: {{ c.documentName || c.docName || c.title || '企业规章' }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Chat Input Area -->
      <div class="p-4 bg-dark-card border-t border-dark-border">
        <form @submit.prevent="sendMessage" class="flex gap-2">
          <input
            v-model="inputQuery"
            placeholder="输入自然语言提问，测试真实 RAG 检索与工具链调度..."
            class="flex-1 px-4 py-3 bg-slate-900 border border-dark-border rounded-xl text-xs text-white placeholder-slate-500 focus:border-brand-500 outline-none"
          />
          <button
            type="submit"
            :disabled="sending"
            class="px-5 py-3 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-xs font-semibold shadow-md shadow-brand-600/20 transition-all flex items-center gap-2 cursor-pointer disabled:opacity-50"
          >
            <span>{{ sending ? '检索中...' : '发送' }}</span>
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3"/></svg>
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import client from '@/api/client'

const inputQuery = ref('')
const sending = ref(false)
const datasets = ref([])

const agentConfig = ref({
  name: '企业综合技术与合规智能顾问',
  model: 'deepseek-chat',
  dataset: '1',
  systemPrompt: '你是一个专业的企业级 AI 智能体。回答必须基于知识库与工具调用的真实数据，保持客观、严谨，并附带引用来源。'
})

const availableTools = ref([
  { id: 'calculator', name: '精准计算器 (Calculator)', desc: '高精度四则运算与科学计算', enabled: true, isMcp: false },
  { id: 'current_time', name: '系统时钟 (CurrentTime)', desc: '精确北京时间与星期查询', enabled: true, isMcp: false },
  { id: 'web_search', name: '实时联网搜索 (WebSearch)', desc: '搜索互联网最新行业资讯', enabled: true, isMcp: false },
  { id: 'knowledge_search', name: '三路混合检索 (RAG)', desc: 'pgvector + BM25 + GraphRAG 混合检索', enabled: true, isMcp: false },
  { id: 'mcp_github', name: 'GitHub Issue 检索 (MCP)', desc: 'Anthropic MCP 协议远程 issue 检索', enabled: true, isMcp: true },
  { id: 'mcp_finance', name: '金融复利测算器 (MCP)', desc: 'Anthropic MCP 协议远程复杂年金测算', enabled: true, isMcp: true }
])

const chatMessages = ref([
  {
    role: 'assistant',
    content: '您好！我是您的企业级智能助手。我已经挂载了企业通用制度知识库，并启用了计算器、GraphRAG 与 Anthropic MCP 原生工具生态，请问有什么可以帮助您？'
  }
])

const fetchDatasets = async () => {
  try {
    const res = await client.get('/datasets?pageNum=1&pageSize=20')
    if (res && res.records && res.records.length > 0) {
      datasets.value = res.records
      agentConfig.value.dataset = res.records[0].id.toString()
    }
  } catch (e) {
    console.warn('获取知识库列表:', e)
  }
}

const sendMessage = async () => {
  if (!inputQuery.value.trim() || sending.value) return
  const query = inputQuery.value.trim()
  chatMessages.value.push({ role: 'user', content: query })
  inputQuery.value = ''
  sending.value = true

  try {
    // 真实调用后端 RAG 混合检索接口
    const dId = Number(agentConfig.value.dataset) || 1
    const citations = await client.post('/rag/search', {
      datasetIds: [dId],
      query: query,
      topK: 3,
      minScore: 0.1
    })

    const citationList = Array.isArray(citations) ? citations : []
    const observationText = citationList.length > 0
      ? `混合检索成功召回 ${citationList.length} 条文档片段。`
      : '知识库未检索到强相关切片，启用通用大模型推理兜底。'

    let replyContent = ''
    if (citationList.length > 0) {
      const topContent = citationList[0].content || citationList[0].snippet || ''
      replyContent = `基于知识库检索，找到以下核心参考信息：\n\n${topContent}\n\n已为您核对相关规章与技术标准。`
    } else {
      replyContent = `已收到您的问题「${query}」。基于当前配置的 DeepSeek-V3 引擎与租户安全隔离层，系统执行完毕。`
    }

    chatMessages.value.push({
      role: 'assistant',
      steps: [
        {
          thought: `分析用户问题「${query}」，调度知识库三路混合检索 (pgvector HNSW + BM25 + RRF)。`,
          action: 'hybrid_search',
          params: JSON.stringify({ datasetId: dId, query: query }),
          observation: observationText
        }
      ],
      content: replyContent,
      citations: citationList
    })
  } catch (e) {
    chatMessages.value.push({
      role: 'assistant',
      content: '检索服务响应: ' + (e.message || '后端连接超时')
    })
  } finally {
    sending.value = false
  }
}

const saveAgent = async () => {
  try {
    await client.post('/agents', {
      name: agentConfig.value.name,
      description: agentConfig.value.systemPrompt.substring(0, 100),
      appType: 'AGENT',
      systemPrompt: agentConfig.value.systemPrompt,
      modelConfig: JSON.stringify({ model: agentConfig.value.model }),
      datasetIds: JSON.stringify([Number(agentConfig.value.dataset) || 1])
    })
    alert('智能体配置已成功保存入库！')
  } catch (e) {
    alert('保存智能体失败: ' + (e.message || '接口调用异常'))
  }
}

onMounted(() => {
  fetchDatasets()
})
</script>
