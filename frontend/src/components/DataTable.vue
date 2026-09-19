<script setup lang="ts">
import StateBlock from './StateBlock.vue'

/**
 * 表格外框。
 *
 * 只负责表头、边框、三种状态与横向滚动；行内容由使用方写 <tr>，
 * 因为每个模块的单元格构成差别很大，抽象成列配置反而会加一层绕路。
 */
export interface Column {
  key: string
  label: string
  width?: string
  align?: 'left' | 'right'
}

defineProps<{
  columns: Column[]
  state?: 'ready' | 'loading' | 'empty' | 'error'
  errorDetail?: string
  emptyTitle?: string
  emptyDetail?: string
  minWidth?: string
}>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="wrap">
    <StateBlock v-if="state === 'loading'" state="loading" :rows="Math.min(columns.length + 2, 6)" />
    <StateBlock
      v-else-if="state === 'error'"
      state="error"
      :detail="errorDetail"
      @retry="$emit('retry')"
    />
    <StateBlock
      v-else-if="state === 'empty'"
      state="empty"
      :title="emptyTitle"
      :detail="emptyDetail"
    >
      <slot name="empty-action" />
    </StateBlock>

    <div v-else class="scroll">
      <table :style="{ minWidth: minWidth ?? '720px' }">
        <thead>
          <tr>
            <th
              v-for="c in columns"
              :key="c.key"
              :style="{ width: c.width, textAlign: c.align ?? 'left' }"
              scope="col"
            >
              {{ c.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <slot />
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  width: 100%;
}

.scroll {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--t-sm);
}

th {
  position: sticky;
  top: 0;
  background: var(--face);
  border-bottom: 1px solid var(--line-strong);
  padding: var(--s-2) var(--s-3);
  font-size: var(--t-xs);
  font-weight: 600;
  color: var(--ink-muted);
  letter-spacing: 0.02em;
  white-space: nowrap;
}

:deep(td) {
  border-bottom: 1px solid var(--line);
  padding: var(--s-2) var(--s-3);
  vertical-align: middle;
}

:deep(tbody tr:last-child td) {
  border-bottom: 0;
}

:deep(tbody tr:hover td) {
  background: var(--face);
}
</style>
