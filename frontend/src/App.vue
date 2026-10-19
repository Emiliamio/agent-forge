<template>
  <div class="flex min-h-screen bg-dark-bg">
    <Navbar v-if="!isLoginPage" />
    <main class="flex-1 overflow-y-auto max-h-screen">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Navbar from './components/Navbar.vue'

const route = useRoute()
const isLoginPage = computed(() => route.path === '/login')
</script>
