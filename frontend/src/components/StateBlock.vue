<script setup lang="ts">
/**
 * 三种状态的统一呈现。
 *
 * 加载用骨架条而不是居中转圈，因为骨架能预告即将出现的内容形状；
 * 空状态负责教人怎么把它填满；出错状态要说清问题和补救办法。
 */
withDefaults(
  defineProps<{
    state: 'loading' | 'empty' | 'error'
    title?: string
    detail?: string
    rows?: number
  }>(),
  { rows: 4 },
)
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="state === 'loading'" class="skeleton" aria-busy="true" aria-live="polite">
    <span class="sr">正在加载</span>
    <div v-for="i in rows" :key="i" class="skeleton__row">
      <span class="skeleton__bar" :style="{ width: `${88 - i * 9}%` }"></span>
    </div>
  </div>

  <div v-else-if="state === 'empty'" class="state">
    <p class="state__title">{{ title ?? '这里还没有内容' }}</p>
    <p v-if="detail" class="state__detail">{{ detail }}</p>
    <slot />
  </div>

  <div v-else class="state state--error" role="alert">
    <p class="state__title">{{ title ?? '这一块没能加载出来' }}</p>
    <p class="state__detail">{{ detail }}</p>
    <button class="state__retry" type="button" @click="$emit('retry')">重新加载</button>
  </div>
</template>

<style scoped>
.skeleton {
  padding: var(--s-4);
}

.skeleton__row {
  padding: var(--s-2) 0;
  border-bottom: 1px solid var(--line);
}

.skeleton__row:last-child {
  border-bottom: 0;
}

.skeleton__bar {
  display: block;
  height: 12px;
  background: var(--ground);
  animation: pulse 1.4s var(--ease) infinite;
}

.skeleton__row:nth-child(2) .skeleton__bar {
  animation-delay: 120ms;
}

.skeleton__row:nth-child(3) .skeleton__bar {
  animation-delay: 240ms;
}

.skeleton__row:nth-child(4) .skeleton__bar {
  animation-delay: 360ms;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

.state {
  padding: var(--s-8) var(--s-4);
  text-align: center;
}

.state__title {
  font-size: var(--t-base);
  font-weight: 600;
}

.state__detail {
  margin-top: var(--s-2);
  font-size: var(--t-sm);
  color: var(--ink-muted);
  max-width: 46ch;
  margin-inline: auto;
}

.state--error .state__title {
  color: var(--bad);
}

.state__retry {
  margin-top: var(--s-4);
  background: transparent;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  padding: 0 var(--s-3);
  height: var(--control-h);
  font-size: var(--t-sm);
  cursor: pointer;
}

.state__retry:hover {
  border-color: var(--ink-muted);
  background: var(--face);
}
</style>
