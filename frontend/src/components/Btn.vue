<script setup lang="ts">
/**
 * 按钮。三种形态在同一个世界里各有明确用途，不做第四种。
 *   solid  主操作，石墨底配冷白字（约 15.2:1）
 *   line   次操作，1px 描边，透明底
 *   quiet  第三级，只有文字
 */
withDefaults(
  defineProps<{
    variant?: 'solid' | 'line' | 'quiet'
    disabled?: boolean
    loading?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'line', disabled: false, loading: false, type: 'button' },
)
</script>

<template>
  <button
    :type="type"
    class="btn"
    :class="[`btn--${variant}`, { 'is-busy': loading }]"
    :disabled="disabled || loading"
  >
    <span v-if="loading" class="btn__spin" aria-hidden="true"></span>
    <slot />
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  gap: var(--s-2);
  padding: 0 var(--s-3);
  height: 30px;
  border-radius: var(--radius);
  border: 1px solid transparent;
  background: transparent;
  font-size: var(--t-sm);
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease),
    border-color var(--dur) var(--ease), transform var(--dur) var(--ease);
}

.btn:active:not(:disabled) {
  transform: translateY(1px);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.btn--solid {
  background: var(--structure);
  color: var(--face);
  border-color: var(--structure);
}

.btn--solid:hover:not(:disabled) {
  background: var(--structure-deep);
  border-color: var(--structure-deep);
}

.btn--line {
  border-color: var(--line-strong);
  color: var(--ink);
}

.btn--line:hover:not(:disabled) {
  border-color: var(--ink-muted);
  background: var(--face-raised);
}

.btn--quiet {
  color: var(--accent-deep);
  padding: 0 var(--s-2);
}

.btn--quiet:hover:not(:disabled) {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.btn__spin {
  width: 10px;
  height: 10px;
  border: 1.5px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 700ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
