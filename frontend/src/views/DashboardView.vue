<template>
  <div class="p-8 max-w-7xl mx-auto space-y-8">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-white tracking-tight">企业控制台概览</h2>
        <p class="text-sm text-slate-400 mt-1">全局监控混合 RAG 召回、响应式 DAG 工作流与多租户 Token 计量</p>
      </div>
      <div class="flex items-center gap-3">
        <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
          <span class="w-2 h-2 rounded-full bg-emerald-400 animate-ping"></span>
          服务全链路健康 (Postgres + Redis)
        </span>
      </div>
    </div>

    <!-- Metrics Grid -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
      <!-- Metric 1: Total Tokens -->
      <div class="p-6 rounded-2xl bg-dark-card border border-dark-border relative overflow-hidden group hover:border-brand-500/50 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">总 Token 消耗</span>
          <div class="p-2.5 rounded-xl bg-brand-500/10 text-brand-400">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
          </div>
        </div>
        <div class="mt-4 flex items-baseline gap-2">
          <span class="text-3xl font-bold text-white">1,428,500</span>
          <span class="text-xs font-medium text-emerald-400">+12.4%</span>
        </div>
        <p class="mt-1 text-xs text-slate-500">毫秒级审计流水</p>
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
          <span class="text-3xl font-bold text-white">68.2%</span>
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
          <span class="text-3xl font-bold text-white">¥ 842.60</span>
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
          <span class="text-3xl font-bold text-white">¥ 5,000.00</span>
          <span class="text-xs font-medium text-amber-400">充足</span>
        </div>
        <p class="mt-1 text-xs text-slate-500">支持欠费毫秒级熔断</p>
      </div>
    </div>

    <!-- Quick Action Cards -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
      <router-link to="/copilot" class="p-6 rounded-2xl bg-gradient-to-br from-amber-500/10 via-dark-card to-slate-900 border border-amber-500/30 hover:border-amber-400 group transition-all relative overflow-hidden">
        <div class="absolute top-3 right-3">
          <span class="text-[10px] px-2 py-0.5 rounded-full bg-amber-400/20 text-amber-300 font-bold border border-amber-400/40">NEW</span>
        </div>
        <div class="w-12 h-12 rounded-xl bg-amber-500/20 text-amber-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"/></svg>
        </div>
        <h3 class="text-base font-bold text-white group-hover:text-amber-400 transition-colors">全员 Copilot 门户 (员工端)</h3>
        <p class="text-xs text-slate-400 mt-2 leading-relaxed">
          极简免培训对话门户，支持制度查阅、合同合规自检、财务报销及 Word/PDF 拖拽问答。
        </p>
        <span class="inline-flex items-center gap-1 text-xs font-medium text-amber-400 mt-4">打开员工门户 &rarr;</span>
      </router-link>

      <router-link to="/datasets" class="p-6 rounded-2xl bg-gradient-to-b from-dark-card to-slate-900 border border-dark-border hover:border-brand-500/50 group transition-all">
        <div class="w-12 h-12 rounded-xl bg-brand-500/10 text-brand-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"/></svg>
        </div>
        <h3 class="text-base font-bold text-white group-hover:text-brand-400 transition-colors">混合检索知识库 (Hybrid RAG)</h3>
        <p class="text-xs text-slate-400 mt-2 leading-relaxed">
          集成 pgvector HNSW 稠密检索 + BM25 全文稀疏检索 + RRF 倒数排名融合 + Cross-Encoder 深度重排。
        </p>
        <span class="inline-flex items-center gap-1 text-xs font-medium text-brand-400 mt-4">进入知识库 &rarr;</span>
      </router-link>

      <router-link to="/workflows" class="p-6 rounded-2xl bg-gradient-to-b from-dark-card to-slate-900 border border-dark-border hover:border-indigo-500/50 group transition-all">
        <div class="w-12 h-12 rounded-xl bg-indigo-500/10 text-indigo-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>
        </div>
        <h3 class="text-base font-bold text-white group-hover:text-indigo-400 transition-colors">DAG 工作流可视化编排</h3>
        <p class="text-xs text-slate-400 mt-2 leading-relaxed">
          基于 Kahn 拓扑排序与 Project Reactor 响应式流，支持多分支并行、条件剪枝与节点超时熔断。
        </p>
        <span class="inline-flex items-center gap-1 text-xs font-medium text-indigo-400 mt-4">打开工作流画布 &rarr;</span>
      </router-link>

      <router-link to="/agents" class="p-6 rounded-2xl bg-gradient-to-b from-dark-card to-slate-900 border border-dark-border hover:border-emerald-500/50 group transition-all">
        <div class="w-12 h-12 rounded-xl bg-emerald-500/10 text-emerald-400 flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 8h2a2 2 0 012 2v6a2 2 0 01-2 2h-2v4l-4-4H9a1.994 1.994 0 01-1.414-.586m0 0L11 14h4a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2v4l.586-.586z"/></svg>
        </div>
        <h3 class="text-base font-bold text-white group-hover:text-emerald-400 transition-colors">ReAct 智能体与工具箱</h3>
        <p class="text-xs text-slate-400 mt-2 leading-relaxed">
          内置 Function Calling 工具生态（计算器/时间/搜索/知识库），动态加载 OpenAPI 3.0 规范与多轮推理。
        </p>
        <span class="inline-flex items-center gap-1 text-xs font-medium text-emerald-400 mt-4">调试智能体 &rarr;</span>
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
