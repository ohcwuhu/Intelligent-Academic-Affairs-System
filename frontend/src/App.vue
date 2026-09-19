<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()

onMounted(() => {
  if (!auth.ready) void auth.restore()
})

const bare = computed(() => route.meta.public === true || !auth.isLoggedIn)
</script>

<template>
  <RouterView v-if="bare" />
  <AppShell v-else />
</template>
