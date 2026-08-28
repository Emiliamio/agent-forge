<template>
  <div class="p-8 max-w-7xl mx-auto space-y-8">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-white tracking-tight">商业计量与计费中心</h2>
        <p class="text-sm text-slate-400 mt-1">毫秒级 Token 消耗审计、Redis 语义向量降本分析与租户钱包管理</p>
      </div>
      <button @click="showRechargeModal = true" class="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-sm font-medium transition-all shadow-md shadow-emerald-600/20 flex items-center gap-2">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"/></svg>
        租户钱包在线充值
      </button>
    </div>

    <!-- Cards Row -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <!-- Wallet Balance -->
      <div class="p-6 rounded-2xl bg-gradient-to-br from-dark-card to-slate-900 border border-dark-border space-y-3">
        <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">可用钱包余额</span>
        <div class="flex items-baseline gap-2">
          <span class="text-3xl font-bold text-white">¥ 5,000.00</span>
          <span class="text-xs text-emerald-400 font-mono">正常服务中</span>
        </div>
        <p class="text-xs text-slate-400 pt-2 border-t border-dark-border">
          支持欠费毫秒级熔断阻断，防穿透防透支
        </p>
      </div>

      <!-- Semantic Cache Savings -->
      <div class="p-6 rounded-2xl bg-gradient-to-br from-dark-card to-slate-900 border border-dark-border space-y-3">
        <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">Redis 语义缓存累计降本</span>
        <div class="flex items-baseline gap-2">
          <span class="text-3xl font-bold text-emerald-400">¥ 842.60</span>
          <span class="text-xs text-slate-400 font-mono">68.2% 命中率</span>
        </div>
        <p class="text-xs text-slate-400 pt-2 border-t border-dark-border">
          余弦相似度 &ge; 0.95 判定，0 成本秒级返回
        </p>
      </div>

      <!-- Pricing Matrix -->
      <div class="p-6 rounded-2xl bg-gradient-to-br from-dark-card to-slate-900 border border-dark-border space-y-3">
        <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">当前模型阶梯费率</span>
        <div class="space-y-1.5 font-mono text-xs text-slate-300">
          <div class="flex justify-between">
            <span>DeepSeek-V3</span>
            <span class="text-brand-400">¥ 1.0 / M (入) · ¥ 2.0 / M (出)</span>
          </div>
          <div class="flex justify-between">
            <span>OpenAI GPT-4o</span>
            <span class="text-indigo-400">¥ 15 / M (入) · ¥ 60 / M (出)</span>
          </div>
        </div>
        <p class="text-[11px] text-slate-500 pt-2 border-t border-dark-border">
          按实际物理承载模型阶梯核算
        </p>
      </div>
    </div>

    <!-- Token Usage Logs Table -->
    <div class="p-6 rounded-2xl bg-dark-card border border-dark-border space-y-4">
      <div class="flex items-center justify-between">
        <h3 class="text-base font-bold text-white flex items-center gap-2">
          <span class="w-2 h-2 rounded-full bg-brand-400"></span>
          Token 消耗审计明细流水
        </h3>
        <span class="text-xs text-slate-400 font-mono">共 4 条调用记录</span>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
          <thead class="bg-slate-900/60 text-slate-400 font-semibold uppercase tracking-wider border-b border-dark-border">
            <tr>
              <th class="py-3 px-4">调用时间</th>
              <th class="py-3 px-4">调用模型</th>
              <th class="py-3 px-4">Prompt Tokens</th>
              <th class="py-3 px-4">Completion Tokens</th>
              <th class="py-3 px-4">总 Token</th>
              <th class="py-3 px-4">语义缓存</th>
              <th class="py-3 px-4">扣费金额</th>
              <th class="py-3 px-4">耗时</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-dark-border text-slate-300 font-mono">
            <tr v-for="(log, i) in usageLogs" :key="i" class="hover:bg-slate-800/40 transition-colors">
              <td class="py-3.5 px-4 text-slate-400">{{ log.time }}</td>
              <td class="py-3.5 px-4 font-medium text-white">{{ log.model }}</td>
              <td class="py-3.5 px-4">{{ log.promptTokens }}</td>
              <td class="py-3.5 px-4">{{ log.completionTokens }}</td>
              <td class="py-3.5 px-4 font-bold text-brand-400">{{ log.totalTokens }}</td>
              <td class="py-3.5 px-4">
                <span v-if="log.isCached" class="px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-[10px]">
                  &check; 命中缓存 (¥0)
                </span>
                <span v-else class="px-2 py-0.5 rounded bg-slate-800 text-slate-400 text-[10px]">
                  未命中
                </span>
              </td>
              <td class="py-3.5 px-4 text-emerald-400 font-bold">{{ log.cost }}</td>
              <td class="py-3.5 px-4 text-slate-500">{{ log.duration }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const showRechargeModal = ref(false)

const usageLogs = ref([
  { time: '2026-08-28 10:45:12', model: 'deepseek-chat', promptTokens: 124, completionTokens: 350, totalTokens: 474, isCached: true, cost: '¥ 0.000000', duration: '24ms' },
  { time: '2026-08-28 10:44:05', model: 'deepseek-chat', promptTokens: 512, completionTokens: 680, totalTokens: 1192, isCached: false, cost: '¥ 0.001872', duration: '1,240ms' },
  { time: '2026-08-28 10:42:30', model: 'gpt-4o', promptTokens: 256, completionTokens: 420, totalTokens: 676, isCached: false, cost: '¥ 0.029040', duration: '2,150ms' },
  { time: '2026-08-28 10:40:18', model: 'deepseek-chat', promptTokens: 1024, completionTokens: 890, totalTokens: 1914, isCached: true, cost: '¥ 0.000000', duration: '18ms' }
])
</script>
