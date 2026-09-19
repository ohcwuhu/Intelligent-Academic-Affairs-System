<script setup lang="ts">
/**
 * 铭牌：内容容器。
 *
 * 一块金属铭牌的特征是平、有边框、没有投影。全站只有这一种容器，
 * 不做卡片套卡片。层次靠 1px 线与留白，不靠阴影。
 */
withDefaults(defineProps<{ title?: string; note?: string; flush?: boolean }>(), {
  flush: false,
})
</script>

<template>
  <section class="plate" :class="{ 'plate--flush': flush }">
    <header v-if="title || $slots.actions" class="plate__head">
      <h2 v-if="title" class="plate__title">{{ title }}</h2>
      <p v-if="note" class="plate__note">{{ note }}</p>
      <div class="plate__actions">
        <slot name="actions" />
      </div>
    </header>
    <slot />
  </section>
</template>

<style scoped>
.plate {
  background: var(--face-raised);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.plate__head {
  display: flex;
  align-items: baseline;
  gap: var(--s-4);
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
}

.plate__title {
  font-size: var(--t-md);
  font-weight: 700;
  letter-spacing: 0.02em;
}

.plate__note {
  font-size: var(--t-xs);
  color: var(--ink-muted);
  flex: 1;
}

.plate__actions {
  margin-left: auto;
  display: flex;
  gap: var(--s-2);
  align-items: center;
}

.plate--flush {
  border: 0;
  border-radius: 0;
  background: transparent;
}
</style>
