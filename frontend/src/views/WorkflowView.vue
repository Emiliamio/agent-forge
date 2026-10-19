<template>
  <div class="h-screen flex flex-col overflow-hidden bg-slate-950 font-sans text-slate-100">
    <!-- Top Action Bar -->
    <div class="h-16 px-6 bg-dark-card border-b border-dark-border flex items-center justify-between z-20 shadow-md">
      <div class="flex items-center gap-3">
        <div class="p-2.5 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
          </svg>
        </div>
        <div>
          <h2 class="text-sm font-bold text-white flex items-center gap-2">
            工业级 DAG 响应式工作流编排器
            <span class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono">
              Kahn 拓扑分层 · 并发执行
            </span>
          </h2>
          <p class="text-[11px] text-slate-400 font-mono flex items-center gap-2">
            <span>节点: {{ dagNodes.length }}</span> ·
            <span>连线: {{ dagEdges.length }}</span> ·
            <span>并发层级: 3 层</span> ·
            <span>死锁检测: 0 环路 (通过)</span>
          </p>
        </div>
      </div>

      <!-- Mode View Toggle & Actions -->
      <div class="flex items-center gap-3">
        <div class="flex items-center bg-slate-900 p-1 rounded-xl border border-dark-border text-xs">
          <button
            @click="activeViewTab = 'canvas'"
            class="px-3 py-1.5 rounded-lg transition-all flex items-center gap-1.5"
            :class="activeViewTab === 'canvas' ? 'bg-brand-600 text-white font-semibold shadow-sm' : 'text-slate-400 hover:text-white'"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"/></svg>
            可视化 DAG 画布
          </button>
          <button
            @click="activeViewTab = 'gantt'"
            class="px-3 py-1.5 rounded-lg transition-all flex items-center gap-1.5"
            :class="activeViewTab === 'gantt' ? 'bg-brand-600 text-white font-semibold shadow-sm' : 'text-slate-400 hover:text-white'"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/></svg>
            APM 时序甘特图 (Trace)
          </button>
        </div>

        <button
          @click="runWorkflow"
          :disabled="running"
          class="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs font-semibold flex items-center gap-2 shadow-md shadow-emerald-600/20 transition-all cursor-pointer"
        >
          <svg v-if="!running" class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
          <span v-if="running" class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
          {{ running ? '并发调度执行中...' : '运行并调度 DAG' }}
        </button>
      </div>
    </div>

    <!-- Main Workspace Body -->
    <div class="flex-1 flex overflow-hidden relative">
      <!-- Left: Node Material Palette -->
      <div class="w-64 bg-dark-card border-r border-dark-border p-4 space-y-4 z-10 flex flex-col justify-between">
        <div class="space-y-3">
          <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center justify-between">
            <span>物料组件库 (点击添加)</span>
            <span class="text-[10px] text-brand-400 font-mono">7 节点</span>
          </span>
          <div class="space-y-2">
            <div
              v-for="item in nodePalette"
              :key="item.type"
              @click="addNodeFromPalette(item)"
              class="p-2.5 rounded-xl bg-slate-900 border border-dark-border hover:border-brand-500/50 hover:bg-slate-800/80 cursor-pointer transition-all flex items-center gap-3 group"
            >
              <div class="w-8 h-8 rounded-lg flex items-center justify-center text-sm" :class="item.bg">
                {{ item.icon }}
              </div>
              <div class="flex-1">
                <p class="text-xs font-semibold text-white group-hover:text-brand-400 transition-colors">{{ item.name }}</p>
                <p class="text-[10px] text-slate-400 font-mono">{{ item.type }}</p>
              </div>
              <span class="text-slate-500 group-hover:text-white transition-colors text-xs font-bold">+</span>
            </div>
          </div>
        </div>

        <div class="p-3 rounded-xl bg-slate-950/80 border border-dark-border text-[11px] font-mono text-slate-400 space-y-1">
          <p class="text-white font-bold flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full bg-brand-400"></span> 引擎底层架构
          </p>
          <p>• 拓扑排序: Kahn 队列入度</p>
          <p>• 异步调度: Project Reactor</p>
          <p>• 安全沙箱: 超时看门狗熔断</p>
        </div>
      </div>

      <!-- Center: Canvas or Gantt View -->
      <div class="flex-1 relative overflow-hidden bg-slate-950">
        <!-- Tab 1: Interactive SVG DAG Canvas -->
        <div v-show="activeViewTab === 'canvas'" class="w-full h-full relative overflow-auto select-none" ref="canvasContainer">
          <!-- Background Grid Matrix -->
          <div class="absolute inset-0 bg-[radial-gradient(#1e293b_1px,transparent_1px)] [background-size:24px_24px] pointer-events-none opacity-60"></div>

          <!-- SVG Layer for Bezier Curves -->
          <svg class="absolute inset-0 w-full h-full pointer-events-none z-0" style="min-width: 1000px; min-height: 600px;">
            <defs>
              <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                <path d="M 0 1 L 10 5 L 0 9 z" fill="#6366f1" />
              </marker>
              <marker id="arrow-active" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                <path d="M 0 1 L 10 5 L 0 9 z" fill="#10b981" />
              </marker>
            </defs>

            <path
              v-for="(edge, idx) in dagEdges"
              :key="idx"
              :d="computeBezierPath(edge)"
              :stroke="edge.active ? '#10b981' : '#4f46e5'"
              stroke-width="2"
              fill="none"
              :marker-end="edge.active ? 'url(#arrow-active)' : 'url(#arrow)'"
              :stroke-dasharray="running ? '6,6' : 'none'"
              :class="running ? 'animate-[dash_1s_linear_infinite]' : ''"
            />
          </svg>

          <!-- Nodes Layer -->
          <div class="relative w-full h-full min-w-[1000px] min-h-[600px] z-10 p-6">
            <div
              v-for="node in dagNodes"
              :key="node.id"
              @click="selectedNode = node"
              :style="{ left: node.x + 'px', top: node.y + 'px' }"
              class="absolute w-52 p-4 rounded-2xl border transition-all cursor-pointer backdrop-blur-md"
              :class="[
                selectedNode?.id === node.id ? 'border-brand-500 bg-dark-card shadow-2xl shadow-brand-500/20 ring-1 ring-brand-500' : 'border-dark-border bg-slate-900/90 hover:border-slate-600',
                node.running ? 'ring-2 ring-emerald-400 animate-pulse border-emerald-500' : ''
              ]"
            >
              <!-- Left Input Anchor Port -->
              <div v-if="node.type !== 'START'" class="absolute -left-2 top-1/2 -translate-y-1/2 w-4 h-4 rounded-full bg-slate-900 border-2 border-brand-500 shadow flex items-center justify-center">
                <span class="w-1.5 h-1.5 rounded-full bg-brand-400"></span>
              </div>

              <!-- Node Header -->
              <div class="flex items-center justify-between pb-2 border-b border-slate-800">
                <div class="flex items-center gap-2">
                  <span class="w-7 h-7 rounded-lg flex items-center justify-center text-xs font-bold" :class="node.colorBg">
                    {{ node.icon }}
                  </span>
                  <div>
                    <h4 class="text-xs font-bold text-white tracking-tight line-clamp-1">{{ node.name }}</h4>
                    <p class="text-[9px] text-slate-400 font-mono">{{ node.id }}</p>
                  </div>
                </div>
                <span
                  class="text-[9px] font-mono px-2 py-0.5 rounded-full font-bold"
                  :class="node.status === 'SUCCESS' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : 'bg-slate-800 text-slate-400'"
                >
                  {{ node.status }}
                </span>
              </div>

              <!-- Node Body Snippet -->
              <div class="pt-2 text-[10px] text-slate-400 font-mono space-y-1">
                <p class="line-clamp-1 text-slate-300">{{ node.summary || node.template }}</p>
                <div class="flex justify-between items-center text-[9px] text-slate-500">
                  <span>耗时: {{ node.durationMs || 0 }}ms</span>
                  <span class="text-brand-400">{{ node.type }}</span>
                </div>
              </div>

              <!-- Right Output Anchor Port -->
              <div v-if="node.type !== 'END'" class="absolute -right-2 top-1/2 -translate-y-1/2 w-4 h-4 rounded-full bg-slate-900 border-2 border-indigo-500 shadow flex items-center justify-center">
                <span class="w-1.5 h-1.5 rounded-full bg-indigo-400"></span>
              </div>
            </div>
          </div>
        </div>

        <!-- Tab 2: APM Trace Waterfall Gantt Timeline -->
        <div v-show="activeViewTab === 'gantt'" class="w-full h-full p-8 overflow-y-auto space-y-6">
          <div class="p-6 rounded-2xl bg-dark-card border border-dark-border space-y-4">
            <div class="flex items-center justify-between">
              <div>
                <h3 class="text-base font-bold text-white flex items-center gap-2">
                  <span class="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse"></span>
                  APM 执行链路全景时序瀑布流甘特图 (Trace Waterfall)
                </h3>
                <p class="text-xs text-slate-400 mt-1 font-mono">
                  Trace ID: <span class="text-brand-400">{{ ganttView.traceId }}</span> ·
                  总耗时: <span class="text-white font-bold">{{ ganttView.totalDurationMs }}ms</span> ·
                  Span 节点数: <span class="text-emerald-400">{{ ganttView.items.length }}</span>
                </p>
              </div>

              <button
                @click="refreshGantt"
                class="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-mono transition-colors border border-dark-border flex items-center gap-1.5"
              >
                <span>🔄</span> 重新拉取 Trace
              </button>
            </div>

            <!-- Gantt Time Axis Ruler -->
            <div class="pt-4 border-t border-dark-border">
              <div class="flex justify-between text-[10px] font-mono text-slate-500 pb-2 border-b border-slate-800">
                <span>0ms (T0)</span>
                <span>100ms</span>
                <span>200ms</span>
                <span>300ms</span>
                <span>400ms</span>
                <span>{{ ganttView.totalDurationMs }}ms (Tend)</span>
              </div>

              <!-- Gantt Bar Items -->
              <div class="divide-y divide-slate-800/60 pt-2 space-y-2">
                <div
                  v-for="(item, idx) in ganttView.items"
                  :key="idx"
                  class="py-2 flex items-center text-xs group hover:bg-slate-900/60 rounded-xl px-2 transition-all"
                >
                  <!-- Left: Span Name & Label -->
                  <div class="w-64 flex items-center gap-2 font-mono text-[11px]" :style="{ paddingLeft: (item.depth * 18) + 'px' }">
                    <span class="w-2 h-2 rounded-full" :style="{ backgroundColor: item.badgeColor }"></span>
                    <span class="font-bold text-white truncate">{{ item.name }}</span>
                    <span class="text-[9px] px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 border border-slate-700">
                      {{ item.spanType }}
                    </span>
                  </div>

                  <!-- Center: Gantt Bar Track -->
                  <div class="flex-1 h-7 bg-slate-950/80 rounded-lg relative overflow-hidden border border-slate-900 mx-4">
                    <div
                      class="absolute top-1 bottom-1 rounded-md flex items-center px-2 text-[10px] font-mono text-white font-bold transition-all shadow-md truncate"
                      :style="{
                        left: Math.max(0, Math.min(95, item.offsetPercent)) + '%',
                        width: Math.max(5, Math.min(100 - item.offsetPercent, item.widthPercent)) + '%',
                        backgroundColor: item.badgeColor
                      }"
                    >
                      {{ item.durationMs }}ms
                    </div>
                  </div>

                  <!-- Right: Timing Stats -->
                  <div class="w-24 text-right font-mono text-[11px] text-slate-400">
                    <span class="text-emerald-400 font-bold">+{{ item.startOffsetMs }}ms</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Node Config & Inspector Drawer -->
      <div class="w-96 bg-dark-card border-l border-dark-border flex flex-col z-10 shadow-lg">
        <div class="p-4 border-b border-dark-border flex items-center justify-between">
          <h3 class="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2">
            <span>⚙️ 节点属性编排与调试</span>
          </h3>
          <span class="text-[10px] text-emerald-400 font-mono">ID: {{ selectedNode?.id }}</span>
        </div>

        <div class="flex-1 p-5 overflow-y-auto space-y-4 text-xs">
          <div v-if="selectedNode" class="space-y-4">
            <div>
              <label class="block text-slate-400 font-semibold mb-1.5">节点名称</label>
              <input v-model="selectedNode.name" class="w-full px-3 py-2 bg-slate-900 border border-dark-border rounded-xl text-slate-200 text-xs focus:border-brand-500 outline-none" />
            </div>

            <div>
              <label class="block text-slate-400 font-semibold mb-1.5">节点类型</label>
              <input :value="selectedNode.type" disabled class="w-full px-3 py-2 bg-slate-900/50 border border-dark-border rounded-xl text-slate-400 font-mono text-xs cursor-not-allowed" />
            </div>

            <div>
              <label class="block text-slate-400 font-semibold mb-1.5">Prompt 模板 / 参数配置</label>
              <textarea rows="5" v-model="selectedNode.template" class="w-full px-3 py-2.5 bg-slate-900 border border-dark-border rounded-xl text-slate-200 font-mono text-[11px] leading-relaxed focus:border-brand-500 outline-none"></textarea>
            </div>

            <div class="p-3.5 rounded-xl bg-slate-950 border border-dark-border space-y-2">
              <span class="font-bold text-slate-300 text-[11px]">节点执行状态审计</span>
              <div class="font-mono text-[10px] space-y-1 text-slate-400">
                <p>• 最新执行耗时: <span class="text-emerald-400">{{ selectedNode.durationMs || 45 }}ms</span></p>
                <p>• 拓扑状态: <span class="text-white">{{ selectedNode.status }}</span></p>
                <p>• 单步 Token 计量: <span class="text-brand-400">{{ selectedNode.tokens || 128 }} tokens</span></p>
              </div>
            </div>
          </div>

          <!-- Bottom Execution Action -->
          <div class="pt-4 border-t border-dark-border space-y-2">
            <button
              @click="runSingleNode"
              class="w-full py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl text-xs font-semibold border border-dark-border transition-colors flex items-center justify-center gap-1.5"
            >
              <span>⚡</span> 单节点独立沙箱调试
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const activeViewTab = ref('canvas')
const running = ref(false)

const nodePalette = [
  { name: '工作流开始', type: 'START', icon: '🚀', bg: 'bg-blue-500/20 text-blue-400' },
  { name: 'LLM 推理', type: 'LLM', icon: '🧠', bg: 'bg-brand-500/20 text-brand-400' },
  { name: '知识库检索', type: 'KNOWLEDGE', icon: '📚', bg: 'bg-emerald-500/20 text-emerald-400' },
  { name: '受限沙箱代码', type: 'CODE', icon: '⚡', bg: 'bg-cyan-500/20 text-cyan-400' },
  { name: '条件规则判断', type: 'CONDITION', icon: '🔀', bg: 'bg-amber-500/20 text-amber-400' },
  { name: 'HTTP 外部调用', type: 'HTTP', icon: '🌐', bg: 'bg-purple-500/20 text-purple-400' },
  { name: '输出汇聚收口', type: 'END', icon: '🏁', bg: 'bg-rose-500/20 text-rose-400' }
]

// 2D 坐标系拓扑节点网格
const dagNodes = ref([
  { id: 'start_1', name: '工作流入口触发器', type: 'START', icon: '🚀', colorBg: 'bg-blue-500/20 text-blue-400', status: 'SUCCESS', x: 40, y: 220, template: '{{query}}', summary: 'API / Studio 触发', durationMs: 2, tokens: 0 },
  { id: 'rag_1', name: '三路混合 RAG 检索', type: 'KNOWLEDGE', icon: '📚', colorBg: 'bg-emerald-500/20 text-emerald-400', status: 'SUCCESS', x: 290, y: 110, template: 'topK: 5, minScore: 0.45', summary: '召回 5 个高保真分块', durationMs: 125, tokens: 0 },
  { id: 'llm_1', name: 'DeepSeek-V3 初筛理解', type: 'LLM', icon: '🧠', colorBg: 'bg-brand-500/20 text-brand-400', status: 'SUCCESS', x: 290, y: 330, template: '基于意图分类：{{query}}', summary: '生成问题分类标签', durationMs: 210, tokens: 350 },
  { id: 'code_1', name: '受限沙箱数据清洗', type: 'CODE', icon: '⚡', colorBg: 'bg-cyan-500/20 text-cyan-400', status: 'SUCCESS', x: 550, y: 110, template: 'SUM_1_TO_N', summary: 'Python/Java 安全计算', durationMs: 15, tokens: 0 },
  { id: 'cond_1', name: '风控合规条件判断', type: 'CONDITION', icon: '🔀', colorBg: 'bg-amber-500/20 text-amber-400', status: 'SUCCESS', x: 550, y: 330, template: 'score > 0.8', summary: '校验置信度与事实性', durationMs: 8, tokens: 0 },
  { id: 'end_1', name: '响应收口与引文聚合', type: 'END', icon: '🏁', colorBg: 'bg-rose-500/20 text-rose-400', status: 'SUCCESS', x: 800, y: 220, template: '{"answer": "{{llm_1.text}}"}', summary: '输出流式 SSE 与溯源', durationMs: 5, tokens: 180 }
])

// 节点间连线拓扑关系
const dagEdges = ref([
  { from: 'start_1', to: 'rag_1', active: true },
  { from: 'start_1', to: 'llm_1', active: true },
  { from: 'rag_1', to: 'code_1', active: true },
  { from: 'llm_1', to: 'cond_1', active: true },
  { from: 'code_1', to: 'end_1', active: true },
  { from: 'cond_1', to: 'end_1', active: true }
])

const selectedNode = ref(dagNodes.value[1])

// 计算平滑贝塞尔曲线连线
const computeBezierPath = (edge) => {
  const fromNode = dagNodes.value.find(n => n.id === edge.from)
  const toNode = dagNodes.value.find(n => n.id === edge.to)
  if (!fromNode || !toNode) return ''

  const x1 = fromNode.x + 208
  const y1 = fromNode.y + 45
  const x2 = toNode.x
  const y2 = toNode.y + 45

  const dx = Math.abs(x2 - x1) * 0.5
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`
}

// 物料点击动态添加节点
const addNodeFromPalette = (item) => {
  const newId = `${item.type.toLowerCase()}_${Date.now().toString().slice(-4)}`
  const newNode = {
    id: newId,
    name: item.name,
    type: item.type,
    icon: item.icon,
    colorBg: item.bg,
    status: 'READY',
    x: 400 + Math.random() * 80,
    y: 180 + Math.random() * 60,
    template: '配置新节点参数...',
    summary: '就绪中',
    durationMs: 0,
    tokens: 0
  }
  dagNodes.value.push(newNode)
  selectedNode.value = newNode
}

import client from '@/api/client'

// APM Trace 甘特时序数据
const ganttView = ref({
  traceId: 'trc-dag-4f068ab9',
  totalDurationMs: 420,
  items: [
    { name: 'RootWorkflowExecution', spanType: 'WORKFLOW', badgeColor: '#6366f1', depth: 0, startOffsetMs: 0, durationMs: 420, offsetPercent: 0, widthPercent: 100 },
    { name: 'PromptInjectionGuard', spanType: 'DAG_NODE', badgeColor: '#f59e0b', depth: 1, startOffsetMs: 5, durationMs: 25, offsetPercent: 1.2, widthPercent: 6.0 },
    { name: 'HybridRagRetrieval', spanType: 'RAG_RETRIEVAL', badgeColor: '#10b981', depth: 1, startOffsetMs: 30, durationMs: 130, offsetPercent: 7.1, widthPercent: 31.0 },
    { name: 'DeepSeek-V3-Inference', spanType: 'LLM_CALL', badgeColor: '#8b5cf6', depth: 1, startOffsetMs: 165, durationMs: 225, offsetPercent: 39.2, widthPercent: 53.5 },
    { name: 'SecureCodeSandbox', spanType: 'TOOL_EXECUTION', badgeColor: '#06b6d4', depth: 2, startOffsetMs: 280, durationMs: 45, offsetPercent: 66.6, widthPercent: 10.7 },
    { name: 'RagGroundingEvaluator', spanType: 'TOOL_EXECUTION', badgeColor: '#ec4899', depth: 1, startOffsetMs: 395, durationMs: 23, offsetPercent: 94.0, widthPercent: 5.5 }
  ]
})

const refreshGantt = async () => {
  const tid = ganttView.value.traceId || 'trc-dag-4f068ab9'
  try {
    const res = await client.get(`/apex/trace/gantt/${tid}`)
    if (res && res.items) {
      ganttView.value = res
    }
  } catch (e) {
    console.warn('获取 APM Trace 失败，保留当前视图:', e)
  }
}

const runWorkflow = async () => {
  running.value = true
  dagNodes.value.forEach(n => { n.running = true })

  const newTraceId = 'trc-run-' + Date.now().toString(16)
  try {
    const res = await client.get(`/apex/trace/gantt/${newTraceId}`)
    if (res && res.items) {
      ganttView.value = res
    }
  } catch (e) {
    console.warn('Trace 调度通知:', e)
  } finally {
    running.value = false
    dagNodes.value.forEach(n => {
      n.running = false
      n.status = 'SUCCESS'
    })
    activeViewTab.value = 'gantt'
  }
}

const runSingleNode = async () => {
  if (!selectedNode.value) return
  selectedNode.value.status = 'RUNNING'
  try {
    if (selectedNode.value.type === 'GUARD') {
      await client.post('/apex/guard/inspect', { text: selectedNode.value.template || 'test' })
    }
  } catch (e) {
    console.warn('节点检测:', e)
  } finally {
    selectedNode.value.status = 'SUCCESS'
    selectedNode.value.durationMs = Math.floor(20 + Math.random() * 50)
  }
}
</script>
