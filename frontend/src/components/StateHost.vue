<script setup lang="ts">
/**
 * 状态宿主：有数据时渲染插槽，其余情况交给 StateBlock。
 * 让页面只写一份正常态结构，异常态与空态自动接管。
 */
import StateBlock from './StateBlock.vue'

defineProps<{
  state: 'loading' | 'ready' | 'empty' | 'error'
  errorDetail?: string
  emptyTitle?: string
  emptyDetail?: string
  skeletonRows?: number
}>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="state === 'ready'" class="host">
    <slot />
  </div>
  <StateBlock v-else-if="state === 'loading'" state="loading" :rows="skeletonRows ?? 5" />
  <StateBlock
    v-else-if="state === 'empty'"
    state="empty"
    :title="emptyTitle"
    :detail="emptyDetail"
  />
  <StateBlock v-else state="error" :detail="errorDetail" @retry="$emit('retry')" />
</template>

<style scoped>
.host {
  display: flex;
  flex-direction: column;
  gap: var(--s-6);
}
</style>
