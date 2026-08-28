<template>
  <div class="h-screen flex overflow-hidden">
    <!-- Left: Agent Configuration -->
    <div class="w-1/2 p-8 overflow-y-auto border-r border-dark-border space-y-6">
      <div>
        <h2 class="text-2xl font-bold text-white tracking-tight">ReAct 智能体编排与调试</h2>
        <p class="text-sm text-slate-400 mt-1">配置 System Prompt、挂载知识库与勾选动态 Function Calling 工具箱</p>
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
            <label class="block text-xs font-semibold text-slate-400 mb-1.5">挂载知识库</label>
            <select v-model="agentConfig.dataset" class="w-full px-4 py-2.5 bg-dark-card border border-dark-border rounded-xl text-sm text-white focus:border-brand-500 outline-none">
              <option value="1">企业通用制度知识库 (128 分块)</option>
              <option value="2">金融合规与财务报表 (64 分块)</option>
            </select>
          </div>
        </div>

        <div>
          <label class="block text-xs font-semibold text-slate-400 mb-1.5">系统人设与提示词 (System Prompt)</label>
          <textarea rows="5" v-model="agentConfig.systemPrompt" class="w-full px-4 py-3 bg-dark-card border border-dark-border rounded-xl text-xs text-slate-200 focus:border-brand-500 outline-none font-mono leading-relaxed"></textarea>
        </div>

        <!-- Dynamic Tools Checklist -->
        <div>
          <label class="block text-xs font-semibold text-slate-400 mb-2">启用 Function Calling 工具生态</label>
          <div class="grid grid-cols-2 gap-3">
            <label v-for="tool in availableTools" :key="tool.id" class="p-3.5 rounded-xl border bg-dark-card flex items-start gap-3 cursor-pointer transition-all" :class="tool.enabled ? 'border-brand-500 bg-brand-500/5' : 'border-dark-border'">
              <input type="checkbox" v-model="tool.enabled" class="mt-0.5 rounded border-slate-700 text-brand-600 focus:ring-brand-500" />
              <div>
                <p class="text-xs font-bold text-white">{{ tool.name }}</p>
                <p class="text-[10px] text-slate-400 mt-0.5">{{ tool.desc }}</p>
              </div>
            </label>
          </div>
        </div>

        <button @click="saveAgent" class="w-full py-3 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-sm font-semibold shadow-lg shadow-brand-600/20 transition-all">
          保存智能体配置
        </button>
      </div>
    </div>

    <!-- Right: Real-time ReAct Chat Dialog -->
    <div class="w-1/2 flex flex-col bg-slate-950">
      <div class="h-16 px-6 bg-dark-card border-b border-dark-border flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse"></span>
          <span class="text-xs font-bold text-white">ReAct 推理状态机实时对话调试</span>
        </div>
        <span class="text-[11px] text-slate-500 font-mono">Thought &rarr; Action &rarr; Observation</span>
      </div>

      <!-- Messages Area -->
      <div class="flex-1 p-6 overflow-y-auto space-y-4">
        <div v-for="(msg, i) in chatMessages" :key="i" class="space-y-2">
          <div :class="msg.role === 'user' ? 'flex justify-end' : 'flex justify-start'">
            <div
              :class="msg.role === 'user' ? 'bg-brand-600 text-white rounded-2xl rounded-tr-sm px-4 py-2.5 text-xs max-w-lg' : 'bg-dark-card border border-dark-border text-slate-200 rounded-2xl rounded-tl-sm p-4 text-xs max-w-xl space-y-3'"
            >
              <!-- Thought / Reasoning Steps Accordion -->
              <div v-if="msg.steps && msg.steps.length" class="space-y-2 pb-3 border-b border-slate-700">
                <div v-for="(step, sidx) in msg.steps" :key="sidx" class="p-2.5 rounded-lg bg-slate-900/90 border border-slate-800 text-[11px] font-mono space-y-1">
                  <p class="text-amber-400 font-semibold">🔍 Thought #{{ sidx + 1 }}: {{ step.thought }}</p>
                  <p class="text-brand-400 font-semibold">⚡ Action: {{ step.action }} ({{ step.params }})</p>
                  <p class="text-slate-400">📋 Observation: {{ step.observation }}</p>
                </div>
              </div>

              <!-- Final Answer -->
              <p class="leading-relaxed whitespace-pre-wrap">{{ msg.content }}</p>

              <!-- Citations Badges -->
              <div v-if="msg.citations && msg.citations.length" class="pt-2 border-t border-slate-800 flex flex-wrap gap-2 text-[10px]">
                <span v-for="(cite, cidx) in msg.citations" :key="cidx" class="px-2 py-0.5 rounded bg-brand-500/10 text-brand-300 border border-brand-500/20 font-mono">
                  [引用 #{{ cidx + 1 }}] {{ cite.docName }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Input Area -->
      <div class="p-4 bg-dark-card border-t border-dark-border flex gap-3">
        <input
          v-model="inputQuery"
          type="text"
          placeholder="向智能体提问（可触发数学计算、知识库检索或联网搜索）..."
          class="flex-1 px-4 py-3 bg-slate-900 border border-dark-border rounded-xl text-xs text-white outline-none focus:border-brand-500"
          @keyup.enter="sendMessage"
        />
        <button @click="sendMessage" class="px-5 py-3 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-xs font-semibold">
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const inputQuery = ref('')

const agentConfig = ref({
  name: '企业综合技术与合规智能顾问',
  model: 'deepseek-chat',
  dataset: '1',
  systemPrompt: '你是一个专业的企业级 AI 智能体。回答必须基于知识库与工具调用的真实数据，保持客观、严谨，并附带引用来源。'
})

const availableTools = ref([
  { id: 'calculator', name: '精准计算器 (Calculator)', desc: '高精度四则运算与科学计算', enabled: true },
  { id: 'current_time', name: '系统时钟 (CurrentTime)', desc: '精确北京时间与星期查询', enabled: true },
  { id: 'web_search', name: '实时联网搜索 (WebSearch)', desc: '搜索互联网最新行业资讯', enabled: true },
  { id: 'knowledge_search', name: '知识库检索 (RAG)', desc: '私有制度与文档多路混合检索', enabled: true }
])

const chatMessages = ref([
  {
    role: 'assistant',
    content: '您好！我是您的企业级智能助手。我已经挂载了企业通用制度知识库，并启用了计算器与多路混合检索工具，请问有什么可以帮助您？'
  },
  {
    role: 'user',
    content: '请帮我查一下，公司多租户数据隔离机制是怎么实现的？另外如果今年研发投入 240 万，同比去年增长 25%，去年的投入是多少？'
  },
  {
    role: 'assistant',
    steps: [
      { thought: '首先从知识库中检索多租户数据隔离机制的实现方案。', action: 'knowledge_search', params: '{"query": "多租户数据隔离机制"}', observation: '召回 2 条文档块：MyBatis-Plus AST 语法树拦截与 tenant_id 物理隔离。' },
      { thought: '接着使用精准计算器计算去年的研发投入：240 / (1 + 0.25)。', action: 'calculator', params: '{"expression": "240 / 1.25"}', observation: '计算结果为 192.00 万元。' }
    ],
    content: '为您整理回答如下：\n\n1. 【多租户数据隔离机制】\n系统在底层基于 MyBatis-Plus 的 TenantLineInnerInterceptor 插件，在 JsqlParser SQL AST 语法树编译层级自动追加 `AND tenant_id = ?`，全自动杜绝越权。\n\n2. 【研发投入核算】\n根据公式计算：240 ÷ 1.25 = **192.00 万元**。去年公司研发投入为 192 万元。',
    citations: [
      { docName: '多租户安全与隔离规范.pdf' },
      { docName: 'AgentForge 架构白皮书.md' }
    ]
  }
])

const sendMessage = () => {
  if (!inputQuery.value.trim()) return
  const query = inputQuery.value
  chatMessages.value.push({ role: 'user', content: query })
  inputQuery.value = ''

  setTimeout(() => {
    chatMessages.value.push({
      role: 'assistant',
      steps: [
        { thought: '分析用户输入，无需调用额外工具，直接由 DeepSeek-V3 生成专业解答。', action: 'none', params: '{}', observation: '直接回复' }
      ],
      content: `已收到您的问题「${query}」。基于当前配置的 DeepSeek-V3 引擎与企业知识库，系统运行正常，全链路多租户逻辑隔离无泄漏。`
    })
  }, 400)
}

const saveAgent = () => {
  alert('智能体配置已更新保存！')
}
</script>
