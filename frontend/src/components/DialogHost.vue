<script setup lang="ts">
/** 对话框宿主：放在 AppShell 里，全站共用一套。 */
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { dialogState, settleDialog } from './useDialog'
import Btn from './Btn.vue'

const input = ref<HTMLInputElement | HTMLTextAreaElement | null>(null)

watch(
  () => dialogState.kind,
  async (kind) => {
    if (kind === 'prompt') {
      await Promise.resolve()
      input.value?.focus()
      if (input.value instanceof HTMLInputElement) input.value.select()
    }
  },
)

function onKey(e: KeyboardEvent) {
  if (!dialogState.kind) return
  if (e.key === 'Escape') settleDialog(false)
  if (e.key === 'Enter' && !dialogState.multiline) settleDialog(true)
}

onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div v-if="dialogState.kind" class="scrim" @click.self="settleDialog(false)">
    <div class="dialog" role="dialog" aria-modal="true" :aria-label="dialogState.title">
      <p class="dialog__title">{{ dialogState.title }}</p>
      <p v-if="dialogState.body" class="dialog__body">{{ dialogState.body }}</p>

      <div v-if="dialogState.kind === 'prompt'" class="dialog__field">
        <label v-if="dialogState.label" class="dialog__label" for="dialog-input">
          {{ dialogState.label }}
          <span v-if="dialogState.required" class="dialog__required">必填</span>
        </label>
        <textarea
          v-if="dialogState.multiline"
          id="dialog-input"
          ref="input"
          v-model="dialogState.value"
          rows="3"
          :placeholder="dialogState.placeholder"
          @keydown.enter.stop
        />
        <input
          v-else
          id="dialog-input"
          ref="input"
          v-model="dialogState.value"
          :placeholder="dialogState.placeholder"
        />
        <p v-if="dialogState.error" class="dialog__error" role="alert">{{ dialogState.error }}</p>
      </div>

      <div class="dialog__act">
        <Btn variant="quiet" @click="settleDialog(false)">{{ dialogState.cancelText }}</Btn>
        <Btn variant="solid" :class="{ 'is-danger': dialogState.danger }" @click="settleDialog(true)">
          {{ dialogState.confirmText }}
        </Btn>
      </div>
    </div>
  </div>
</template>

<style scoped>
.scrim {
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  background: var(--scrim);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--s-6);
}
.dialog {
  width: min(460px, 100%);
  background: var(--face-raised);
  border: 1px solid var(--line-strong);
  padding: var(--s-4);
}
.dialog__title {
  font-size: var(--t-md);
  font-weight: 600;
}
.dialog__body {
  margin-top: var(--s-2);
  font-size: var(--t-sm);
  color: var(--ink-muted);
  white-space: pre-wrap;
}
.dialog__field {
  margin-top: var(--s-3);
}
.dialog__label {
  display: block;
  margin-bottom: var(--s-1);
  font-size: var(--t-sm);
  color: var(--ink-muted);
}
.dialog__required {
  margin-left: var(--s-2);
  padding: 0 5px;
  background: var(--accent);
  color: var(--face);
  font-size: var(--t-2xs);
}
.dialog__field input,
.dialog__field textarea {
  width: 100%;
  padding: 0 var(--s-3);
  height: var(--control-h);
  background: var(--face);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  font: inherit;
  color: inherit;
}
.dialog__field textarea {
  height: auto;
  padding: var(--s-2) var(--s-3);
  resize: vertical;
}
.dialog__error {
  margin-top: var(--s-1);
  font-size: var(--t-sm);
  color: var(--bad);
}
.dialog__act {
  display: flex;
  justify-content: flex-end;
  gap: var(--s-2);
  margin-top: var(--s-4);
}
/* 危险操作：确认键用警示色，避免"顺手点了确定" */
.dialog__act :deep(.btn.is-danger) {
  background: var(--bad);
}
</style>
