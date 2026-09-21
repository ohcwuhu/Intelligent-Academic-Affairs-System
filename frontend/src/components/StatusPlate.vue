<script setup lang="ts">
/**
 * 状态挂牌。
 *
 * 这个世界的状态语汇来自门牌：房间在使用时挂一块牌子，而不是把墙刷成红色。
 * 所以状态用一块小挂牌表示，不用彩色圆点，也不用侧边色条。
 *
 * 对比度：face 字压在 structure 底上约 15.2:1，压在 accent 底上约 5.2:1，
 * 压在 ok 与 bad 底上也都高于 5:1。
 */
defineProps<{ value: string }>()

const toneOf = (v: string): string => {
  if (['开放', '在职', '在读', '启用', '生效', '已通过', '已修正', '已补录'].includes(v)) return 'ok'
  if (['停开', '休学', '离职', '停用', '待处理', '待审'].includes(v)) return 'warn'
  if (['结课', '退学', '已失效', '无需处理', '已驳回', '已撤回'].includes(v)) return 'off'
  return 'plain'
}
</script>

<template>
  <span class="plate" :class="`plate--${toneOf(value)}`">{{ value }}</span>
</template>

<style scoped>
.plate {
  display: inline-block;
  font-size: var(--t-xs);
  line-height: 1.6;
  padding: 0 6px;
  white-space: nowrap;
}

.plate--ok {
  background: var(--structure);
  color: var(--face);
}

.plate--warn {
  background: var(--accent);
  color: var(--face);
  font-weight: 600;
}

.plate--off {
  background: var(--line-strong);
  color: var(--ink);
}

.plate--plain {
  border: 1px solid var(--line-strong);
  color: var(--ink-muted);
}
</style>
