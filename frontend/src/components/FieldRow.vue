<script setup lang="ts">
/**
 * 表单项。标签固定放在输入框上方，不使用占位符当标签；
 * 错误信息固定出现在输入框下方。
 */
defineProps<{ label: string; hint?: string; error?: string; forId?: string }>()
</script>

<template>
  <div class="field">
    <label class="field__label" :for="forId">{{ label }}</label>
    <slot />
    <p v-if="error" class="field__error">{{ error }}</p>
    <p v-else-if="hint" class="field__hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: var(--s-2);
  min-width: 0;
}

.field__label {
  font-size: var(--t-xs);
  font-weight: 600;
  color: var(--ink-muted);
}

.field__hint {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}

.field__error {
  font-size: var(--t-xs);
  color: var(--bad);
}

:deep(input),
:deep(select),
:deep(textarea) {
  width: 100%;
  height: 30px;
  padding: 0 var(--s-2);
  background: var(--face-raised);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  font-size: var(--t-sm);
  transition: border-color var(--dur) var(--ease);
}

:deep(textarea) {
  height: auto;
  padding: var(--s-2);
}

:deep(input:hover),
:deep(select:hover) {
  border-color: var(--ink-muted);
}

:deep(input::placeholder) {
  color: var(--ink-muted);
}
</style>
