<template>
  <aside class="w-64 bg-dark-card border-r border-dark-border flex flex-col justify-between h-screen sticky top-0">
    <div>
      <!-- Brand Logo -->
      <div class="h-16 flex items-center px-6 gap-3 border-b border-dark-border">
        <div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-brand-600 to-indigo-400 flex items-center justify-center shadow-lg shadow-brand-500/20">
          <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
        </div>
        <div>
          <h1 class="font-bold text-base tracking-wide text-white flex items-center gap-1.5">
            AgentForge <span class="text-[10px] bg-brand-500/20 text-brand-400 border border-brand-500/30 px-1.5 py-0.2 rounded">PRO</span>
          </h1>
          <p class="text-[11px] text-slate-400 font-mono">Enterprise AI Platform</p>
        </div>
      </div>

      <!-- Navigation Links -->
      <nav class="p-4 space-y-1.5">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="flex items-center justify-between px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all duration-150"
          :class="isActive(item.path) ? 'bg-brand-600 text-white shadow-md shadow-brand-600/20' : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'"
        >
          <div class="flex items-center gap-3">
            <component :is="item.icon" class="w-4 h-4" />
            <span>{{ item.name }}</span>
          </div>
          <span v-if="item.badge" class="text-[10px] px-1.5 py-0.5 rounded-md bg-amber-400/20 text-amber-300 border border-amber-400/30 font-medium">
            {{ item.badge }}
          </span>
        </router-link>
      </nav>
    </div>

    <!-- Bottom Tenant & Status -->
    <div class="p-4 border-t border-dark-border space-y-3">
      <!-- Tenant Badge -->
      <div class="p-3 bg-slate-900/60 border border-dark-border rounded-xl flex items-center justify-between">
        <div class="flex items-center gap-2.5">
          <div class="w-7 h-7 rounded-lg bg-emerald-500/20 border border-emerald-500/40 flex items-center justify-center text-emerald-400 text-xs font-bold">
            T1
          </div>
          <div>
            <p class="text-xs font-medium text-slate-200">默认主租户</p>
            <p class="text-[10px] text-emerald-400 flex items-center gap-1">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
              pgvector 混合隔离
            </p>
          </div>
        </div>
      </div>

      <!-- Engine Status -->
      <div class="text-[11px] text-slate-500 flex justify-between items-center px-1 font-mono">
        <span>Reactor Virtual Threads</span>
        <span class="text-brand-400">Java 21</span>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  LayoutDashboard,
  Database,
  GitFork,
  Bot,
  Receipt,
  Sparkles
} from 'lucide-vue-next'

const route = useRoute()

const navItems = [
  { name: '控制台概览', path: '/', icon: LayoutDashboard },
  { name: '全员 Copilot 门户', path: '/copilot', icon: Sparkles, badge: '极简门户' },
  { name: '知识库管理 (RAG)', path: '/datasets', icon: Database },
  { name: 'DAG 工作流编排', path: '/workflows', icon: GitFork },
  { name: '智能体调试 (ReAct)', path: '/agents', icon: Bot },
  { name: '商业计量与计费', path: '/billing', icon: Receipt }
]

const isActive = (path) => {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
</script>
