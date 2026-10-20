<template>
  <div class="min-h-screen bg-slate-950 flex items-center justify-center p-6 relative overflow-hidden font-sans">
    <!-- Background Glow Effects -->
    <div class="absolute -top-40 -left-40 w-96 h-96 bg-brand-600/20 rounded-full blur-3xl pointer-events-none" />
    <div class="absolute -bottom-40 -right-40 w-96 h-96 bg-purple-600/15 rounded-full blur-3xl pointer-events-none" />

    <!-- Main Login Card -->
    <div class="w-full max-w-md bg-dark-card border border-dark-border rounded-2xl p-8 space-y-6 shadow-2xl relative z-10 backdrop-blur-md">
      <!-- Brand Header -->
      <div class="text-center space-y-2">
        <div class="w-12 h-12 rounded-2xl bg-gradient-to-tr from-brand-600 to-indigo-500 mx-auto flex items-center justify-center shadow-lg shadow-brand-500/30">
          <svg
            class="w-6 h-6 text-white"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M13 10V3L4 14h7v7l9-11h-7z"
            />
          </svg>
        </div>
        <h2 class="text-2xl font-bold text-white tracking-tight">
          AgentForge 企业级中台
        </h2>
        <p class="text-xs text-slate-400">
          纯血 Java 21 & PostgreSQL pgvector 混合检索架构
        </p>
      </div>

      <!-- Login Form -->
      <form
        class="space-y-4"
        @submit.prevent="handleLogin"
      >
        <!-- Tenant Code -->
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">租户编码 (Tenant Code)</label>
          <input
            v-model="form.tenantCode"
            type="text"
            required
            placeholder="tenant_default"
            class="w-full px-4 py-2.5 bg-slate-900 border border-dark-border rounded-xl text-sm text-white placeholder-slate-500 focus:border-brand-500 focus:ring-1 focus:ring-brand-500 outline-none transition-all font-mono"
          >
        </div>

        <!-- Username -->
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">用户名 (Username)</label>
          <input
            v-model="form.username"
            type="text"
            required
            placeholder="admin"
            class="w-full px-4 py-2.5 bg-slate-900 border border-dark-border rounded-xl text-sm text-white placeholder-slate-500 focus:border-brand-500 focus:ring-1 focus:ring-brand-500 outline-none transition-all"
          >
        </div>

        <!-- Password -->
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">登录密码 (Password)</label>
          <input
            v-model="form.password"
            type="password"
            required
            placeholder="admin123456"
            class="w-full px-4 py-2.5 bg-slate-900 border border-dark-border rounded-xl text-sm text-white placeholder-slate-500 focus:border-brand-500 focus:ring-1 focus:ring-brand-500 outline-none transition-all font-mono"
          >
        </div>

        <!-- Error Alert -->
        <div
          v-if="errorMessage"
          class="p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs flex items-center gap-2"
        >
          <svg
            class="w-4 h-4 flex-shrink-0"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
          <span>{{ errorMessage }}</span>
        </div>

        <!-- Submit Button -->
        <button
          type="submit"
          :disabled="loading"
          class="w-full py-2.5 px-4 bg-brand-600 hover:bg-brand-500 active:bg-brand-700 text-white font-semibold text-sm rounded-xl shadow-lg shadow-brand-600/25 transition-all flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <svg
            v-if="loading"
            class="animate-spin -ml-1 mr-2 h-4 w-4 text-white"
            fill="none"
            viewBox="0 0 24 24"
          >
            <circle
              class="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              stroke-width="4"
            />
            <path
              class="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
            />
          </svg>
          <span>{{ loading ? '身份验证与握手中...' : '安全登录控制台' }}</span>
        </button>
      </form>

      <!-- Enterprise Footer Info -->
      <div class="pt-2 border-t border-dark-border/80 text-center">
        <div class="flex items-center justify-center gap-3 text-[11px] text-slate-500 font-mono">
          <span>Sa-Token 1.38</span>
          <span>&bull;</span>
          <span>pgvector 0.7+</span>
          <span>&bull;</span>
          <span>Java 21 LTS</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import client from '@/api/client'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')

const form = reactive({
  tenantCode: 'tenant_default',
  username: 'admin',
  password: 'admin123456'
})

const handleLogin = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    const res = await client.post('/auth/login', {
      tenantCode: form.tenantCode.trim(),
      username: form.username.trim(),
      password: form.password
    })

    if (res && res.token) {
      localStorage.setItem('agentforge_token', res.token)
      localStorage.setItem('agentforge_tenant_id', String(res.tenantId || '1'))
      localStorage.setItem('agentforge_username', res.username || form.username)
      router.push('/')
    } else {
      errorMessage.value = '服务器未返回有效凭据，请联系系统管理员'
    }
  } catch (err) {
    console.error('登录失败:', err)
    errorMessage.value = err.response?.data?.message || err.message || '网络或接口连接异常'
  } finally {
    loading.value = false
  }
}
</script>
