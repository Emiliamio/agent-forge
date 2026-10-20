<template>
  <div class="p-8 max-w-7xl mx-auto space-y-8">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-2xl font-bold text-white tracking-tight">
          商业计量与计费中心
        </h2>
        <p class="text-sm text-slate-400 mt-1">
          毫秒级 Token 消耗审计、Redis 语义向量降本分析与租户钱包管理
        </p>
      </div>
      <button
        class="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-sm font-medium transition-all shadow-md shadow-emerald-600/20 flex items-center gap-2"
        @click="showRechargeModal = true"
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
          d="M12 6v6m0 0v6m0-6h6m-6 0H6"
        /></svg>
        租户钱包在线充值
      </button>
    </div>

    <!-- Cards Row -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <!-- Wallet Balance -->
      <div class="p-6 rounded-2xl bg-gradient-to-br from-dark-card to-slate-900 border border-dark-border space-y-3">
        <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">可用钱包余额</span>
        <div class="flex items-baseline gap-2">
          <span class="text-3xl font-bold text-white">{{ stats.walletBalance }}</span>
          <span class="text-xs text-emerald-400 font-mono">多租户独立钱包</span>
        </div>
        <p class="text-xs text-slate-400 pt-2 border-t border-dark-border">
          支持欠费毫秒级熔断阻断，防穿透防透支
        </p>
      </div>

      <!-- Semantic Cache Savings -->
      <div class="p-6 rounded-2xl bg-gradient-to-br from-dark-card to-slate-900 border border-dark-border space-y-3">
        <span class="text-xs font-semibold uppercase tracking-wider text-slate-400">Redis 语义缓存累计降本</span>
        <div class="flex items-baseline gap-2">
          <span class="text-3xl font-bold text-emerald-400">{{ stats.costSaved }}</span>
          <span class="text-xs text-slate-400 font-mono">{{ stats.cacheHitRate }} 命中率</span>
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
          <span class="w-2 h-2 rounded-full bg-brand-400" />
          Token 消耗审计明细流水
        </h3>
        <span class="text-xs text-slate-400 font-mono">共 {{ usageLogs.length }} 条调用记录</span>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs">
          <thead class="bg-slate-900/60 text-slate-400 font-semibold uppercase tracking-wider border-b border-dark-border">
            <tr>
              <th class="py-3 px-4">
                调用时间
              </th>
              <th class="py-3 px-4">
                调用模型
              </th>
              <th class="py-3 px-4">
                Prompt Tokens
              </th>
              <th class="py-3 px-4">
                Completion Tokens
              </th>
              <th class="py-3 px-4">
                总 Token
              </th>
              <th class="py-3 px-4">
                语义缓存
              </th>
              <th class="py-3 px-4">
                扣费金额
              </th>
              <th class="py-3 px-4">
                耗时
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-dark-border text-slate-300">
            <tr v-if="usageLogs.length === 0">
              <td
                colspan="8"
                class="py-8 text-center text-slate-500 font-mono"
              >
                暂无 Token 消耗流水记录
              </td>
            </tr>
            <tr
              v-for="(log, idx) in usageLogs"
              :key="idx"
              class="hover:bg-slate-800/40 transition-colors"
            >
              <td class="py-3.5 px-4 font-mono text-slate-400">
                {{ log.createdAt ? log.createdAt.replace('T', ' ').substring(0, 19) : log.time }}
              </td>
              <td class="py-3.5 px-4 font-mono text-white">
                {{ log.modelName || log.model }}
              </td>
              <td class="py-3.5 px-4 font-mono">
                {{ log.promptTokens }}
              </td>
              <td class="py-3.5 px-4 font-mono">
                {{ log.completionTokens }}
              </td>
              <td class="py-3.5 px-4 font-bold text-brand-400 font-mono">
                {{ log.totalTokens }}
              </td>
              <td class="py-3.5 px-4">
                <span
                  v-if="log.isCached"
                  class="px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-[10px] font-mono"
                >
                  &check; 命中缓存 (¥0)
                </span>
                <span
                  v-else
                  class="px-2 py-0.5 rounded bg-slate-800 text-slate-400 text-[10px] font-mono"
                >
                  未命中
                </span>
              </td>
              <td class="py-3.5 px-4 text-emerald-400 font-bold font-mono">
                ¥ {{ Number(log.costAmount || 0).toFixed(6) }}
              </td>
              <td class="py-3.5 px-4 text-slate-500 font-mono">
                {{ log.durationMs ? log.durationMs + 'ms' : '24ms' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Recharge Modal -->
    <div
      v-if="showRechargeModal"
      class="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4"
    >
      <div class="bg-dark-card border border-dark-border rounded-2xl w-full max-w-sm p-6 space-y-5 shadow-2xl">
        <h3 class="text-lg font-bold text-white">
          租户钱包充值
        </h3>
        <p class="text-xs text-slate-400">
          充值金额将即刻汇入当前租户资金账户，支持毫秒级防穿透防护：
        </p>
        <div>
          <label class="block text-xs font-semibold text-slate-400 mb-1.5">充值金额 (元)</label>
          <input
            v-model.number="rechargeAmount"
            type="number"
            min="10"
            step="10"
            class="w-full px-3.5 py-2.5 bg-slate-900 border border-dark-border rounded-xl text-base font-bold text-emerald-400 focus:border-brand-500 outline-none font-mono"
          >
        </div>
        <div class="flex justify-end gap-3 pt-2">
          <button
            class="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-xl transition-colors"
            @click="showRechargeModal = false"
          >
            取消
          </button>
          <button
            :disabled="recharging"
            class="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold rounded-xl shadow-md transition-all"
            @click="handleRecharge"
          >
            {{ recharging ? '充值中...' : '确认充值' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import client from '@/api/client'

const showRechargeModal = ref(false)
const recharging = ref(false)
const rechargeAmount = ref(500)

const stats = ref({
  walletBalance: '¥ 1,000.00',
  cacheHitRate: '0.0%',
  costSaved: '¥ 0.00'
})

const usageLogs = ref([])

const fetchBilling = async () => {
  try {
    const res = await client.get('/billing/stats')
    if (res) {
      stats.value.walletBalance = '¥ ' + Number(res.walletBalance || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
      stats.value.cacheHitRate = ((Number(res.cacheHitRate) || 0) * 100).toFixed(1) + '%'
      const cached = Number(res.cachedCalls || 0)
      stats.value.costSaved = '¥ ' + (cached * 0.015).toFixed(2)
    }
  } catch (e) {
    console.error('获取计费看板数据失败:', e)
  }

  try {
    const logsRes = await client.get('/billing/logs?pageNum=1&pageSize=20')
    if (logsRes && logsRes.records) {
      usageLogs.value = logsRes.records
    }
  } catch (e) {
    console.error('获取 Token 消耗流水失败:', e)
  }
}

const handleRecharge = async () => {
  if (rechargeAmount.value <= 0) return
  recharging.value = true
  try {
    await client.post('/billing/recharge', { amount: rechargeAmount.value })
    alert(`充值成功！已注入 ¥${rechargeAmount.value}.00 到租户可用钱包余额！`)
    showRechargeModal.value = false
    await fetchBilling()
  } catch (e) {
    alert('充值请求失败: ' + (e.message || '网络异常'))
  } finally {
    recharging.value = false
  }
}

onMounted(() => {
  fetchBilling()
})
</script>
