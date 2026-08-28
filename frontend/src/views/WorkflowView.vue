<template>
  <div class="h-screen flex flex-col overflow-hidden">
    <!-- Top Action Bar -->
    <div class="h-16 px-6 bg-dark-card border-b border-dark-border flex items-center justify-between z-10">
      <div class="flex items-center gap-3">
        <div class="p-2 rounded-lg bg-indigo-500/10 text-indigo-400">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>
        </div>
        <div>
          <h2 class="text-sm font-bold text-white flex items-center gap-2">
            企业智能客服与知识问答 DAG 工作流
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">v1.2 运行中</span>
          </h2>
          <p class="text-[11px] text-slate-400 font-mono">Kahn 算法自动分层 · Project Reactor 响应式并发调度</p>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <button
          @click="runWorkflow"
          :disabled="running"
          class="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs font-semibold flex items-center gap-2 shadow-md shadow-emerald-600/20 transition-all"
        >
          <svg v-if="!running" class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
          <span v-if="running" class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
          {{ running ? '调度执行中...' : '运行并调试工作流' }}
        </button>
        <button class="px-4 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-xl text-xs font-semibold transition-all">
          保存拓扑定义
        </button>
      </div>
    </div>

    <!-- Canvas Body -->
    <div class="flex-1 flex overflow-hidden relative bg-slate-950">
      <!-- Left: Node Palette -->
      <div class="w-60 bg-dark-card border-r border-dark-border p-4 space-y-3 z-10">
        <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider">节点物料库 (拖拽添加)</span>
        <div class="space-y-2">
          <div
            v-for="item in nodePalette"
            :key="item.type"
            class="p-3 rounded-xl bg-slate-900 border border-dark-border hover:border-brand-500/50 cursor-grab transition-all flex items-center gap-2.5"
          >
            <div class="w-7 h-7 rounded-lg flex items-center justify-center text-xs" :class="item.bg">
              {{ item.icon }}
            </div>
            <div>
              <p class="text-xs font-semibold text-white">{{ item.name }}</p>
              <p class="text-[10px] text-slate-400 font-mono">{{ item.type }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Center: Visual Interactive DAG Graph -->
      <div class="flex-1 p-8 overflow-auto flex items-center justify-center relative">
        <div class="space-y-8 max-w-xl w-full">
          <div
            v-for="(node, index) in dagNodes"
            :key="node.id"
            @click="selectedNode = node"
            class="p-5 rounded-2xl border transition-all cursor-pointer relative"
            :class="[
              selectedNode?.id === node.id ? 'border-brand-500 bg-dark-card shadow-xl shadow-brand-500/10' : 'border-dark-border bg-slate-900/90',
              node.status === 'RUNNING' ? 'ring-2 ring-emerald-400 animate-pulse' : ''
            ]"
          >
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <span class="w-8 h-8 rounded-xl flex items-center justify-center text-sm font-bold" :class="node.colorBg">
                  {{ node.icon }}
                </span>
                <div>
                  <h4 class="text-xs font-bold text-white">{{ node.name }}</h4>
                  <p class="text-[10px] text-slate-400 font-mono">{{ node.id }} · {{ node.type }}</p>
                </div>
              </div>
              <span class="text-[10px] font-mono px-2 py-0.5 rounded-full" :class="node.statusBg">
                {{ node.statusText }}
              </span>
            </div>

            <!-- Arrow Indicator -->
            <div v-if="index < dagNodes.length - 1" class="absolute -bottom-6 left-1/2 -translate-x-1/2 flex flex-col items-center">
              <svg class="w-4 h-4 text-brand-400 animate-bounce" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3"/>
              </svg>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Node Config & Debug Stream Drawer -->
      <div class="w-96 bg-dark-card border-l border-dark-border flex flex-col z-10">
        <div class="p-4 border-b border-dark-border">
          <h3 class="text-xs font-bold text-white uppercase tracking-wider">节点属性与运行日志</h3>
        </div>
        <div class="flex-1 p-4 overflow-y-auto space-y-4 text-xs">
          <div v-if="selectedNode" class="space-y-3">
            <div>
              <label class="block text-slate-400 mb-1">节点名称</label>
              <input v-model="selectedNode.name" class="w-full px-3 py-2 bg-slate-900 border border-dark-border rounded-lg text-slate-200" />
            </div>
            <div>
              <label class="block text-slate-400 mb-1">节点类型</label>
              <input :value="selectedNode.type" disabled class="w-full px-3 py-2 bg-slate-900/50 border border-dark-border rounded-lg text-slate-400 font-mono" />
            </div>
            <div>
              <label class="block text-slate-400 mb-1">输入/Prompt 模板 (支持 {{ '{' + '{query}' + '}' }})</label>
              <textarea rows="4" v-model="selectedNode.template" class="w-full px-3 py-2 bg-slate-900 border border-dark-border rounded-lg text-slate-200 font-mono text-[11px]"></textarea>
            </div>
          </div>

          <!-- Execution Timeline -->
          <div class="pt-4 border-t border-dark-border space-y-2">
            <span class="font-bold text-slate-400 text-[11px]">最新调度日志快照</span>
            <div class="p-3 rounded-xl bg-slate-950 border border-dark-border font-mono text-[10px] text-slate-300 space-y-1">
              <p class="text-emerald-400">&check; 拓扑环路死锁检测通过 (Kahn Algorithm)</p>
              <p class="text-brand-400">&check; 分层并发拓扑层级: 3 层</p>
              <p class="text-slate-400">&check; 执行总耗时: 124ms · 状态: COMPLETED</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const running = ref(false)

const nodePalette = [
  { name: '开始节点', type: 'START', icon: '🚀', bg: 'bg-blue-500/20 text-blue-400' },
  { name: 'LLM 大模型', type: 'LLM', icon: '🧠', bg: 'bg-brand-500/20 text-brand-400' },
  { name: '知识库检索', type: 'KNOWLEDGE', icon: '📚', bg: 'bg-emerald-500/20 text-emerald-400' },
  { name: '条件分支', type: 'CONDITION', icon: '🔀', bg: 'bg-amber-500/20 text-amber-400' },
  { name: 'HTTP 请求', type: 'HTTP', icon: '🌐', bg: 'bg-purple-500/20 text-purple-400' },
  { name: '数据转换', type: 'CODE', icon: '⚡', bg: 'bg-cyan-500/20 text-cyan-400' },
  { name: '结束节点', type: 'END', icon: '🏁', bg: 'bg-rose-500/20 text-rose-400' }
]

const dagNodes = ref([
  { id: 'start_1', name: '工作流开始', type: 'START', icon: '🚀', colorBg: 'bg-blue-500/20 text-blue-400', statusText: 'SUCCESS', statusBg: 'bg-emerald-500/10 text-emerald-400', template: '{{query}}' },
  { id: 'rag_1', name: '知识库三路检索', type: 'KNOWLEDGE', icon: '📚', colorBg: 'bg-emerald-500/20 text-emerald-400', statusText: 'SUCCESS', statusBg: 'bg-emerald-500/10 text-emerald-400', template: 'topK: 5, minScore: 0.45' },
  { id: 'llm_1', name: 'DeepSeek-V3 推理回答', type: 'LLM', icon: '🧠', colorBg: 'bg-brand-500/20 text-brand-400', statusText: 'SUCCESS', statusBg: 'bg-emerald-500/10 text-emerald-400', template: '基于知识库参考资料：{{rag_1.context}}，严谨回答用户问题：{{query}}' },
  { id: 'end_1', name: '输出汇聚与结果收口', type: 'END', icon: '🏁', colorBg: 'bg-rose-500/20 text-rose-400', statusText: 'SUCCESS', statusBg: 'bg-emerald-500/10 text-emerald-400', template: '{"answer": "{{llm_1.text}}"}' }
])

const selectedNode = ref(dagNodes.value[2])

const runWorkflow = () => {
  running.value = true
  setTimeout(() => {
    running.value = false
    alert('🎉 DAG 工作流响应式调度执行成功！全链路各节点耗时与输出快照已落盘审计！')
  }, 600)
}
</script>
