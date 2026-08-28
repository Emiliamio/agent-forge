<template>
  <div class="flex min-h-screen bg-dark-bg">
    <Navbar />
    <main class="flex-1 overflow-y-auto max-h-screen">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import Navbar from './components/Navbar.vue'
</script>
