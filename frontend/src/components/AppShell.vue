<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { homeForRole, routes } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { useCurrentTerm } from './useTerm'
import { teachingWeek } from '@/utils/format'
import type { Role } from '@/api/types'
import ToastHost from './ToastHost.vue'
import Btn from './Btn.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { currentTerm, load } = useCurrentTerm()

onMounted(() => {
  void load()
})

/** 索引列：按 meta.group 分组，保持路由表中的声明顺序。 */
const navGroups = computed(() => {
  const role = auth.role as Role | null
  const groups = new Map<string, { title: string; path: string; name: string }[]>()
  for (const r of routes) {
    const meta = r.meta as
      | { nav?: boolean; group?: string; title?: string; roles?: Role[] }
      | undefined
    if (!meta?.nav || !r.name || !meta.group) continue
    if (role && meta.roles && !meta.roles.includes(role)) continue
    const list = groups.get(meta.group) ?? []
    list.push({ title: meta.title ?? String(r.name), path: r.path, name: String(r.name) })
    groups.set(meta.group, list)
  }
  return [...groups.entries()].map(([title, items]) => ({ title, items }))
})

const pageTitle = computed(() => (route.meta.title as string | undefined) ?? '')
const week = computed(() => teachingWeek(currentTerm.value))

/** 位置条右端的主操作由路由决定，保证每个页面只有一个明显的主操作。 */
const primaryAction = computed(() => {
  if (route.name === 'my-timetable') return { label: '去选课', to: '/me/select' }
  return null
})

function logout() {
  auth.logout()
  void router.push({ name: 'login' })
}

function goHome() {
  void router.push(homeForRole(auth.role))
}
</script>

<template>
  <div class="shell">
    <header class="bar">
      <button class="bar__mark" type="button" @click="goHome">
        教务系统
        <span class="bar__mark-sub">演示数据，非真实师生信息</span>
      </button>

      <div class="bar__where">
        <span class="bar__seg">{{ currentTerm?.name ?? '学期加载中' }}</span>
        <span class="bar__seg num">{{ week ? `第 ${week} 教学周` : '不在教学周' }}</span>
        <span class="bar__seg bar__seg--here">你在：{{ pageTitle }}</span>
      </div>

      <div class="bar__right">
        <RouterLink v-if="primaryAction" class="bar__cta" :to="primaryAction.to">
          {{ primaryAction.label }}
        </RouterLink>
        <span class="bar__user">
          {{ auth.user?.realName }}
          <span class="bar__role">{{ auth.user?.username }}</span>
        </span>
        <Btn variant="quiet" @click="logout">退出</Btn>
      </div>
    </header>

    <div class="body">
      <nav class="rail" aria-label="功能索引">
        <div v-for="g in navGroups" :key="g.title" class="rail__group">
          <p class="rail__group-title">{{ g.title }}</p>
          <RouterLink
            v-for="item in g.items"
            :key="item.name"
            class="rail__item"
            :class="{ 'is-here': route.name === item.name }"
            :to="item.path"
          >
            {{ item.title }}
          </RouterLink>
        </div>
        <p class="rail__foot">数据为合成演示数据</p>
      </nav>

      <main class="main">
        <RouterView />
      </main>
    </div>

    <ToastHost />
  </div>
</template>

<style scoped>
.shell {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}

.bar {
  display: flex;
  align-items: center;
  gap: var(--s-6);
  height: 56px;
  padding: 0 var(--s-4);
  background: var(--structure);
  color: var(--face);
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
}

.bar__mark {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  background: none;
  border: 0;
  color: inherit;
  cursor: pointer;
  font-size: var(--t-md);
  font-weight: 600;
  letter-spacing: 0.02em;
  padding: 0;
}

.bar__mark-sub {
  font-size: 11px;
  font-weight: 400;
  color: var(--muted);
}

.bar__where {
  display: flex;
  align-items: center;
  font-size: var(--t-sm);
  color: var(--muted);
  min-width: 0;
}

.bar__seg {
  padding: 0 var(--s-3);
  border-left: 1px solid var(--structure-line);
  white-space: nowrap;
}

.bar__seg:first-child {
  border-left: 0;
  padding-left: 0;
}

.bar__seg--here {
  color: var(--face);
}

.bar__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--s-4);
}

.bar__cta {
  color: var(--face);
  text-decoration: none;
  font-size: var(--t-sm);
  border-bottom: 2px solid var(--accent);
  padding-bottom: 1px;
}

.bar__cta:hover {
  color: var(--accent);
}

.bar__user {
  font-size: var(--t-sm);
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.bar__role {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--muted);
}

.bar :deep(.btn--quiet) {
  color: var(--muted);
}

.bar :deep(.btn--quiet:hover) {
  color: var(--face);
}

.body {
  flex: 1;
  display: grid;
  grid-template-columns: 208px minmax(0, 1fr);
  min-height: 0;
}

.rail {
  background: var(--structure-deep);
  color: var(--face);
  padding: var(--s-6) 0 var(--s-4);
  display: flex;
  flex-direction: column;
  gap: var(--s-6);
  position: sticky;
  top: 56px;
  align-self: start;
  max-height: calc(100dvh - 56px);
  overflow-y: auto;
}

.rail__group-title {
  font-size: 11px;
  color: var(--muted);
  padding: 0 var(--s-4) var(--s-2);
  letter-spacing: 0.08em;
}

.rail__item {
  display: block;
  padding: var(--s-2) var(--s-4);
  color: var(--face);
  text-decoration: none;
  font-size: var(--t-sm);
  border-left: 3px solid transparent;
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease);
}

.rail__item:hover {
  background: var(--structure);
}

.rail__item.is-here {
  border-left-color: var(--accent);
  background: var(--structure);
  font-weight: 600;
}

.rail__foot {
  margin-top: auto;
  padding: var(--s-4);
  font-size: 11px;
  color: var(--muted);
  border-top: 1px solid var(--structure-line);
}

.main {
  padding: var(--s-6);
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--s-6);
}

@media (max-width: 900px) {
  .bar {
    height: auto;
    flex-wrap: wrap;
    padding: var(--s-3) var(--s-4);
    gap: var(--s-3);
  }

  .bar__where {
    order: 3;
    width: 100%;
    overflow-x: auto;
  }

  .body {
    grid-template-columns: 1fr;
  }

  .rail {
    position: static;
    max-height: none;
    flex-direction: row;
    gap: 0;
    padding: 0;
    overflow-x: auto;
    border-bottom: 1px solid var(--structure-line);
  }

  .rail__group {
    display: flex;
    align-items: center;
  }

  .rail__group-title,
  .rail__foot {
    display: none;
  }

  .rail__item {
    border-left: 0;
    border-bottom: 3px solid transparent;
    white-space: nowrap;
    padding: var(--s-3) var(--s-4);
  }

  .rail__item.is-here {
    border-left-color: transparent;
    border-bottom-color: var(--accent);
  }

  .main {
    padding: var(--s-4);
  }
}
</style>
