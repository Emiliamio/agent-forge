<template>
  <div class="p-8 max-w-7xl mx-auto space-y-8">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-white tracking-tight">企业控制台概览</h2>
        <p class="text-sm text-slate-400 mt-1">全局监控混合 RAG 召回、响应式 DAG 工作流与多租户 Token 计量</p>
      </div>
      <div class="flex items-center gap-3">
        <span
          class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border transition-all"
          :class="healthInfo.isUp ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 'bg-amber-500/10 text-amber-400 border-amber-500/20'"
        >
          <span class="w-2 h-2 rounded-full" :class="healthInfo.isUp ? 'bg-emerald-400 animate-ping' : 'bg-amber-400'"></span>
          {{ healthInfo.text }}
        </span>
      </div>
    </div>

    <!-- Metrics Grid -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
      <!-- Metric 1: Total Tokens -->
      <div class="p-6 rounded-2xl bg-dark-card border border-dark-border relative overflow-hidden group hover:border-brand-500/50 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">总调用次数</span>
          <div class="p-2.5 rounded-xl bg-brand-500/10 text-brand-400">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
          </div>
        </div>
        <div class="mt-4 flex items-baseline gap-2">
          <span class="text-3xl font-bold text-white">{{ stats.totalCalls }}</span>
          <span class="text-xs font-medium text-emerald-400">实时审计</span>
        </div>
        <p class="mt-1 text-xs text-slate-500">毫秒级审计流水日志</p>
      </div>

      <!-- Metric 2: Semantic Cache Hit Rate -->
      <div class="p-6 rounded-2xl bg-dark-card border border-dark-border relative overflow-hidden group hover:border-emerald-500/50 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">语义缓存命中率</span>
          <div class="p-2.5 rounded-xl bg-emerald-500/10 text-emerald-400">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
          </div>
        </div>
        <div class="mt-4 flex items-baseline gap-2">
          <span class="text-3xl font-bold text-white">{{ stats.cacheHitRate }}</span>
          <span class="text-xs font-medium text-emerald-400">余弦阈值 0.95</span>
        </div>
        <p class="mt-1 text-xs text-slate-500">大模型 API 账单直降 60%+</p>
      </div>

      <!-- Metric 3: Cost Saved -->
      <div class="p-6 rounded-2xl bg-dark-card border border-dark-border relative overflow-hidden group hover:border-indigo-500/50 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">累计节省成本</span>
          <div class="p-2.5 rounded-xl bg-indigo-500/10 text-indigo-400">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
          </div>
        </div>
        <div class="mt-4 flex items-baseline gap-2">
          <span class="text-3xl font-bold text-white">{{ stats.costSaved }}</span>
          <span class="text-xs font-medium text-indigo-400">Redis 0 成本</span>
        </div>
        <p class="mt-1 text-xs text-slate-500">免调用 DeepSeek/OpenAI</p>
      </div>

      <!-- Metric 4: Multi-Tenant Isolation -->
      <div class="p-6 rounded-2xl bg-dark-card border border-dark-border relative overflow-hidden group hover:border-amber-500/50 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">租户钱包余额</span>
          <div class="p-2.5 rounded-xl bg-amber-500/10 text-amber-400">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"/></svg>
          </div>
        </div>
        <div class="mt-4 flex items-baseline gap-2">
          <span class="text-3xl font-bold text-white">{{ stats.walletBalance }}</span>
          <span class="text-xs font-medium text-amber-400">多租户隔离</span>
        </div>
        <p class="mt-1 text-xs text-slate-500">支持欠费毫秒级熔断</p>
      </div>
    </div>

    <!-- Quick Action Cards -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <router-link to="/datasets" class="p-6 rounded-2xl bg-dark-card border border-dark-border hover:border-brand-500/50 transition-all group block">
        <div class="w-12 h-12 rounded-xl bg-brand-500/10 text-brand-400 flex items-center justify-center text-xl mb-4 group-hover:scale-110 transition-transform">
          📚
        </div>
        <h3 class="text-base font-bold text-white group-hover:text-brand-400 transition-colors">知识库数据集 (RAG)</h3>
        <p class="text-xs text-slate-400 mt-2 leading-relaxed">
          管理企业多模态文档切片，配置 pgvector HNSW 稠密余弦向量与 BM25 稀疏全文检索，实现高精度召回。
        </p>
      </router-link>

      <router-link to="/workflows" class="p-6 rounded-2xl bg-dark-card border border-dark-border hover:border-brand-500/50 transition-all group block">
        <div class="w-12 h-12 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center text-xl mb-4 group-hover:scale-110 transition-transform">
          ⚡
        </div>
        <h3 class="text-base font-bold text-white group-hover:text-emerald-400 transition-colors">DAG 工作流编排引擎</h3>
        <p class="text-xs text-slate-400 mt-2 leading-relaxed">
          Kahn 算法拓扑排序与 Reactor 响应式并发调度，可视化编排 LLM、Python 代码沙箱与条件分支。
        </p>
      </router-link>

      <router-link to="/billing" class="p-6 rounded-2xl bg-dark-card border border-dark-border hover:border-brand-500/50 transition-all group block">
        <div class="w-12 h-12 rounded-xl bg-amber-500/10 text-amber-400 flex items-center justify-center text-xl mb-4 group-hover:scale-110 transition-transform">
          💰
        </div>
        <h3 class="text-base font-bold text-white group-hover:text-amber-400 transition-colors">租户计量与账单看板</h3>
        <p class="text-xs text-slate-400 mt-2 leading-relaxed">
          精确到单次请求的 Prompt 与 Completion Token 消耗流水，结合语义缓存节省大模型调用开销。
        </p>
      </router-link>
    </div>

    <!-- Web Component Widget Showcase -->
    <div class="p-6 rounded-2xl bg-gradient-to-r from-slate-900 via-dark-card to-slate-900 border border-dark-border space-y-4">
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <span class="p-1 rounded bg-brand-500/20 text-brand-400">🔌</span>
            两行代码无缝嵌入第三方系统 (Shadow DOM 挂件)
          </h3>
          <p class="text-xs text-slate-400 mt-1">
            企业客户无需改造原有 OA/ERP/CRM 系统，只需在任意 HTML 页面贴入以下脚本，即可获得右下角悬浮 AI 助手：
          </p>
        </div>
        <div class="flex items-center gap-2">
          <button @click="copyWidgetCode" class="px-3.5 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-xs font-semibold shadow-md transition-all">
            📋 复制挂件引入代码
          </button>
          <button @click="triggerWidget" class="px-3.5 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold border border-dark-border transition-all">
            🚀 现场体验右下角挂件
          </button>
        </div>
      </div>

      <div class="p-3.5 rounded-xl bg-slate-950 border border-slate-800 font-mono text-[11px] text-slate-300 overflow-x-auto select-all">
        <code>&lt;script src="/agentforge-widget.js" data-api-url="/api" data-app-id="1" data-title="企业智能助手"&gt;&lt;/script&gt;</code>
      </div>
    </div>

    <!-- Architecture Badges -->
    <div class="p-6 rounded-2xl bg-slate-900/40 border border-dark-border">
      <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-4">企业级硬核技术架构标准</h4>
      <div class="flex flex-wrap gap-2.5">
        <span class="px-3 py-1.5 rounded-lg bg-dark-card border border-dark-border text-xs text-slate-300 font-mono">Java 21 Virtual Threads</span>
        <span class="px-3 py-1.5 rounded-lg bg-dark-card border border-dark-border text-xs text-slate-300 font-mono">PostgreSQL 16 pgvector HNSW</span>
        <span class="px-3 py-1.5 rounded-lg bg-dark-card border border-dark-border text-xs text-slate-300 font-mono">JsqlParser AST 租户隔离</span>
        <span class="px-3 py-1.5 rounded-lg bg-dark-card border border-dark-border text-xs text-slate-300 font-mono">RRF 倒数排名融合</span>
        <span class="px-3 py-1.5 rounded-lg bg-dark-card border border-dark-border text-xs text-slate-300 font-mono">Cross-Encoder Reranker</span>
        <span class="px-3 py-1.5 rounded-lg bg-dark-card border border-dark-border text-xs text-slate-300 font-mono">Kahn 拓扑排序算法</span>
        <span class="px-3 py-1.5 rounded-lg bg-dark-card border border-dark-border text-xs text-slate-300 font-mono">Project Reactor 并发流</span>
        <span class="px-3 py-1.5 rounded-lg bg-dark-card border border-dark-border text-xs text-slate-300 font-mono">Shadow DOM 嵌入挂件</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import client from '@/api/client'

const healthInfo = ref({
  isUp: true,
  text: '正在检测服务全链路健康...'
})

const stats = ref({
  totalCalls: '0',
  cacheHitRate: '0.0%',
  costSaved: '¥ 0.00',
  walletBalance: '¥ 1,000.00'
})

const fetchHealthAndStats = async () => {
  try {
    const health = await client.get('/system/health')
    if (health) {
      const pg = health.postgres === 'UP' ? 'PG: UP' : 'PG: DOWN'
      const r = health.redis === 'UP' ? 'Redis: UP' : 'Redis: DOWN'
      healthInfo.value = {
        isUp: health.postgres === 'UP',
        text: `全链路健康 (${pg} · ${r})`
      }
    }
  } catch (e) {
    healthInfo.value = {
      isUp: false,
      text: '后端服务连接中 (请确认 8080 端口已启动)'
    }
  }

  try {
    const billing = await client.get('/billing/stats')
    if (billing) {
      stats.value.totalCalls = Number(billing.totalCalls || 0).toLocaleString()
      stats.value.cacheHitRate = ((Number(billing.cacheHitRate) || 0) * 100).toFixed(1) + '%'
      stats.value.walletBalance = '¥ ' + Number(billing.walletBalance || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
      const cached = Number(billing.cachedCalls || 0)
      stats.value.costSaved = '¥ ' + (cached * 0.015).toFixed(2)
    }
  } catch (e) {
    // 未登录或租户初始状态
  }
}

onMounted(() => {
  fetchHealthAndStats()
})

const copyWidgetCode = () => {
  const code = '<script src="/agentforge-widget.js" data-api-url="/api" data-app-id="1" data-title="企业智能助手"><' + '/script>'
  navigator.clipboard.writeText(code)
  alert('已复制挂件代码到剪贴板！可直接粘贴至任何第三方系统的 HTML 页面。')
}

const triggerWidget = () => {
  if (!document.getElementById('agentforge-widget-container')) {
    const s = document.createElement('script')
    s.src = '/agentforge-widget.js'
    s.setAttribute('data-api-url', '/api')
    s.setAttribute('data-app-id', '1')
    s.setAttribute('data-title', '企业智能助手')
    document.body.appendChild(s)
    alert('已成功在当前页面注入 Shadow DOM 挂件！请查看屏幕右下角 🤖 悬浮球！')
  } else {
    alert('右下角挂件已就绪，请直接点击右下角的 🤖 悬浮按钮进行交互！')
  }
}
</script>
