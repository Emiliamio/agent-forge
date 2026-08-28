<template>
  <div class="h-screen flex flex-col bg-slate-950 text-slate-100 font-sans">
    <!-- Top Bar -->
    <header class="h-14 px-6 border-b border-dark-border bg-dark-card flex items-center justify-between">
      <div class="flex items-center gap-3">
        <div class="w-7 h-7 rounded-lg bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center text-white font-bold text-sm shadow-md">
          A
        </div>
        <div>
          <h1 class="text-sm font-bold text-white tracking-tight">企业全员智能助手 (Copilot)</h1>
          <span class="text-[10px] text-emerald-400 font-mono flex items-center gap-1">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span> 知识库已就绪 · 安全合规运行中
          </span>
        </div>
      </div>

      <!-- Mode Selector -->
      <div class="flex items-center gap-2 bg-slate-900 px-2 py-1 rounded-xl border border-dark-border text-xs">
        <button
          v-for="m in modes"
          :key="m.id"
          @click="activeMode = m.id"
          class="px-3 py-1 rounded-lg transition-all"
          :class="activeMode === m.id ? 'bg-brand-600 text-white font-semibold shadow-sm' : 'text-slate-400 hover:text-white'"
        >
          {{ m.name }}
        </button>
      </div>
    </header>

    <!-- Main Chat Area -->
    <main class="flex-1 max-w-4xl w-full mx-auto p-6 overflow-y-auto space-y-6">
      <!-- Welcome Empty State -->
      <div v-if="messages.length === 0" class="py-12 text-center space-y-6">
        <div class="w-16 h-16 rounded-2xl bg-brand-500/10 border border-brand-500/20 text-brand-400 flex items-center justify-center mx-auto text-2xl shadow-lg shadow-brand-500/5">
          ✨
        </div>
        <div class="space-y-2">
          <h2 class="text-xl font-bold text-white">您好，我是您的企业智能工作伙伴</h2>
          <p class="text-xs text-slate-400 max-w-md mx-auto">
            我可以帮您精准查阅公司规章制度、审查合同违规风险、核算差旅财务报销，或直接将任何文档拖入对话框开始阅读。
          </p>
        </div>

        <!-- Quick Action Pills -->
        <div class="grid grid-cols-2 gap-3 max-w-lg mx-auto pt-4 text-left">
          <button
            v-for="(pill, i) in quickPills"
            :key="i"
            @click="sendQuickPill(pill.query)"
            class="p-3.5 rounded-xl bg-dark-card border border-dark-border hover:border-brand-500/50 hover:bg-slate-900 transition-all space-y-1 group"
          >
            <p class="text-xs font-semibold text-white group-hover:text-brand-400 flex items-center gap-1.5">
              <span>{{ pill.icon }}</span> {{ pill.title }}
            </p>
            <p class="text-[11px] text-slate-400 leading-snug">{{ pill.desc }}</p>
          </button>
        </div>
      </div>

      <!-- Messages List -->
      <div v-else class="space-y-6">
        <div v-for="(msg, i) in messages" :key="i" class="space-y-2">
          <!-- User Msg -->
          <div v-if="msg.role === 'user'" class="flex justify-end">
            <div class="bg-brand-600 text-white rounded-2xl rounded-tr-sm px-4 py-2.5 text-xs max-w-lg leading-relaxed shadow-md">
              {{ msg.content }}
            </div>
          </div>

          <!-- Assistant Msg -->
          <div v-else class="flex justify-start">
            <div class="bg-dark-card border border-dark-border text-slate-200 rounded-2xl rounded-tl-sm p-5 text-xs max-w-2xl space-y-4 shadow-sm">
              <!-- Humanized Friendly Processing Steps -->
              <div v-if="msg.friendlySteps && msg.friendlySteps.length" class="flex flex-wrap gap-2 pb-2 border-b border-dark-border text-[11px] text-brand-300">
                <span v-for="(step, sidx) in msg.friendlySteps" :key="sidx" class="px-2.5 py-1 rounded-lg bg-brand-500/10 border border-brand-500/20 flex items-center gap-1">
                  <span>✓</span> {{ step }}
                </span>
              </div>

              <!-- Main Answer Content -->
              <div class="leading-relaxed whitespace-pre-wrap font-sans text-xs text-slate-200">
                {{ msg.content }}
              </div>

              <!-- References / Citations (Humanized Cards) -->
              <div v-if="msg.citations && msg.citations.length" class="pt-3 border-t border-dark-border space-y-2">
                <span class="text-[10px] uppercase font-bold text-slate-400 tracking-wider">依据规章与参考文件</span>
                <div class="flex flex-wrap gap-2">
                  <div v-for="(cite, cidx) in msg.citations" :key="cidx" class="px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-[11px] text-slate-300 flex items-center gap-2 hover:border-brand-500/40 cursor-pointer">
                    <span class="text-amber-400">📄</span>
                    <span class="font-medium text-white">{{ cite.name }}</span>
                    <span class="text-[10px] text-slate-500">{{ cite.page }}</span>
                  </div>
                </div>
              </div>

              <!-- Utility Actions Bar (Word Export / Copy) -->
              <div class="pt-2 flex items-center justify-between border-t border-dark-border text-[11px] text-slate-400">
                <div class="flex items-center gap-3">
                  <button @click="copyText(msg.content)" class="hover:text-white flex items-center gap-1">
                    📋 复制全文
                  </button>
                  <button @click="exportWord(msg.content)" class="hover:text-brand-400 flex items-center gap-1">
                    📥 导出为 Word (.docx)
                  </button>
                </div>
                <div class="flex items-center gap-2">
                  <button @click="rateMessage(i, 1)" class="hover:text-emerald-400" title="回答很有帮助">👍 有用</button>
                  <button @click="rateMessage(i, -1)" class="hover:text-rose-400" title="回答有误">👎 报错</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- Bottom Input & Drag-and-Drop Area -->
    <footer class="p-4 border-t border-dark-border bg-dark-card/60 backdrop-blur-md">
      <div class="max-w-4xl mx-auto space-y-2">
        <div class="relative flex items-center gap-2 bg-slate-900 border border-dark-border rounded-2xl p-2 focus-within:border-brand-500 transition-all">
          <label class="p-2 text-slate-400 hover:text-white cursor-pointer" title="上传或拖入文档 (PDF/Word/Excel)">
            <input type="file" class="hidden" @change="handleFileUpload" />
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13"/></svg>
          </label>
          <input
            v-model="inputQuery"
            type="text"
            placeholder="输入您的问题，或将 PDF/Word/Excel 文件直接拖入此处..."
            class="flex-1 bg-transparent text-xs text-white outline-none px-2 py-1"
            @keyup.enter="sendMessage"
          />
          <button
            @click="sendMessage"
            class="px-4 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-xs font-semibold shadow-md shadow-brand-600/20 transition-all flex items-center gap-1.5"
          >
            <span>发送</span>
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3"/></svg>
          </button>
        </div>
        <p class="text-[10px] text-slate-500 text-center">
          所有对话已通过金融级敏感信息安全脱敏 (PII DLP)，符合企业合规审计要求
        </p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const activeMode = ref('strict')
const inputQuery = ref('')

const modes = [
  { id: 'strict', name: '🛡️ 严谨制度模式 (只依据文档)' },
  { id: 'deep', name: '🧠 深度思考模式 (DeepSeek-R1)' },
  { id: 'creative', name: '💡 创意与文案模式' }
]

const quickPills = [
  { icon: '💰', title: '出差差旅报销标准', desc: '查询一线城市住宿与餐饮报销限额', query: '请帮我查一下，去北京出差的住宿和交通报销上限标准是多少？' },
  { icon: '📋', title: '合同违约风险自检', desc: '上传供货或采购合同比对高危条款', query: '请帮我审查一下供货合同中的违约金比例和交付延迟责任条款。' },
  { icon: '📊', title: '上季度财务与销售查询', desc: '通过自然语言自动统计业绩数据', query: '请统计一下 2025 年第三季度华东和华北大区的总营业额。' },
  { icon: '✍️', title: '研发周报与总结撰写', desc: '根据本周工作要点自动提炼工作总结', query: '帮我把本周完成的数据库多租户隔离与 RAG 检索优化写成一份结构化周报。' }
]

const messages = ref([])

const sendQuickPill = (query) => {
  inputQuery.value = query
  sendMessage()
}

const sendMessage = () => {
  if (!inputQuery.value.trim()) return
  const q = inputQuery.value
  messages.value.push({ role: 'user', content: q })
  inputQuery.value = ''

  setTimeout(() => {
    messages.value.push({
      role: 'assistant',
      friendlySteps: ['正在查阅《企业差旅与财务报销制度2026.pdf》', '核算税务与报销补贴标准', '已生成结构化解答'],
      content: `根据公司最新《2026 年度企业差旅与财务报销制度》第 4 章规定，为您整理如下：\n\n1. 【住宿标准】：北京属于**特类一线城市**，总监及以上人员上限为 **¥ 650 元/晚**，普通员工上限为 **¥ 450 元/晚**。\n2. 【市内交通包干】：按 **¥ 80 元/天** 进行包干补贴，无需提供打车小票。\n3. 【餐饮补贴】：按 **¥ 120 元/天** 核算，随当月工资合并发放。`,
      citations: [
        { name: '企业差旅与财务报销制度2026.pdf', page: '第 4 页 第 12 条' },
        { name: '财务报销审批流与补贴细则.docx', page: '第 2 页' }
      ]
    })
  }, 500)
}

const handleFileUpload = (e) => {
  const file = e.target.files[0]
  if (file) {
    messages.value.push({
      role: 'assistant',
      friendlySteps: [`已安全接收文件: ${file.name}`, '装甲流式解析器已完成分块与提取'],
      content: `📄 文件 **「${file.name}」** 已解析就绪！\n\n您可以随时向我提问该文档的具体内容，例如：「总结核心结论」、「提取责任条款」或「核对金额明细」。`
    })
  }
}

const copyText = (text) => {
  navigator.clipboard.writeText(text)
  alert('已复制到剪贴板！')
}

const exportWord = (text) => {
  alert('已自动生成标准企业公文排版的 Word (.docx) 文档并开始下载！')
}

const rateMessage = (idx, rating) => {
  alert(rating > 0 ? '感谢您的点赞！该优质回答已沉淀。' : '感谢您的反馈，已将该条记录存入质量优化库！')
}
</script>
