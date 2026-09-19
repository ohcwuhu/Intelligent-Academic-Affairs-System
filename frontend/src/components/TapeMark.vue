<script setup lang="ts">
/**
 * 胶带封条。
 *
 * 世界里的依据：一间被贴封条的房间，门上斜着一条胶带写明原因。
 * 用来表达"这一项被挡住了"，替代弹窗或红色徽章。
 * 底色 --accent 配 --face 文字，对比度约 5.2:1。
 *
 * 注意：它绝对定位铺满父容器，父容器必须有确定宽度，
 * 否则宽度塌成 0 时胶带会溢出到行外（这个缺陷实际发生过）。
 */
withDefaults(defineProps<{ text: string; inline?: boolean }>(), { inline: false })
</script>

<template>
  <span class="tape" :class="{ 'tape--inline': inline }">
    <span class="tape__text">{{ text }}</span>
  </span>
</template>

<style scoped>
.tape {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  pointer-events: none;
}

.tape__text {
  transform: rotate(-4deg);
  background: var(--accent);
  color: var(--face);
  font-size: var(--t-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  padding: 2px var(--s-3);
  white-space: nowrap;
  animation: stick var(--dur) var(--ease) both;
}

.tape--inline {
  position: static;
  display: inline-grid;
}

.tape--inline .tape__text {
  transform: rotate(-3deg);
}

@keyframes stick {
  from {
    opacity: 0;
    transform: rotate(-4deg) translateY(-6px) scale(0.96);
  }
  to {
    opacity: 1;
    transform: rotate(-4deg) translateY(0) scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .tape__text {
    animation: none;
  }
}
</style>
