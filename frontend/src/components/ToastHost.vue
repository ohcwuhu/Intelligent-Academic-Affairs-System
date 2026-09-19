<script setup lang="ts">
import { toasts } from './useToast'
</script>

<template>
  <div class="host" role="status" aria-live="polite">
    <p v-for="t in toasts" :key="t.id" class="toast" :class="`toast--${t.tone}`">
      {{ t.text }}
    </p>
  </div>
</template>

<style scoped>
.host {
  position: fixed;
  left: 50%;
  bottom: var(--s-6);
  transform: translateX(-50%);
  z-index: var(--z-toast);
  display: flex;
  flex-direction: column;
  gap: var(--s-2);
  align-items: center;
  pointer-events: none;
}

.toast {
  background: var(--structure);
  color: var(--face);
  padding: var(--s-2) var(--s-4);
  font-size: var(--t-sm);
  max-width: 52ch;
  animation: rise var(--dur) var(--ease) both;
}

/* 状态用整块底色表达，不用侧边色条 */
.toast--bad {
  background: var(--bad);
}

.toast--ok {
  background: var(--ok);
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .toast {
    animation: none;
  }
}
</style>
