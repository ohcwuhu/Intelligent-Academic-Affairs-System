<script setup lang="ts">
/**
 * 登录页。
 *
 * 这是全站唯一的说服型界面：访客只想进门，不需要被推销。
 * 视觉档位 VARIANCE 3 / MOTION 3 / DENSITY 5，取自"信任优先"一档。
 *
 * 右侧不是装饰插图，而是一份真实的账号目录，答辩演示时可一键填入。
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { homeForRole } from '@/router'
import { useAuthStore } from '@/stores/auth'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'

const auth = useAuthStore()
const router = useRouter()

const form = reactive({ username: '', password: '' })
const error = ref('')
const busy = ref(false)

const accounts = [
  { username: '2022001', role: '学生', note: '有重修记录与时间冲突' },
  { username: '2021002', role: '学生', note: '常规学业记录' },
  { username: 't1001', role: '教师', note: '张伟，三门教学班' },
  { username: 't1002', role: '教师', note: '王芳，用于验证数据隔离' },
  { username: 'jw001', role: '教务管理员', note: '全校教务业务、知识库治理' },
  { username: 'admin', role: '系统管理员', note: '含账号管理' },
]

function fill(username: string) {
  form.username = username
  form.password = '123456'
  error.value = ''
}

async function submit() {
  error.value = ''
  if (!form.username.trim() || !form.password) {
    error.value = '请填写账号与口令'
    return
  }
  busy.value = true
  try {
    const user = await auth.login(form.username.trim(), form.password)
    await router.replace(homeForRole(user.role))
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败，请稍后重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="door">
    <section class="plate">
      <div class="plate__brand">
        <p class="plate__name">教务系统</p>
        <p class="plate__sub">基于检索增强生成的智能教务系统</p>
      </div>

      <h1 class="plate__title">登录教务系统</h1>
      <p class="plate__lead">在校师生与教务人员使用统一账号登录。</p>

      <form class="plate__form" @submit.prevent="submit">
        <FieldRow label="账号" for-id="username" hint="学号、工号或管理员账号">
          <input id="username" v-model="form.username" autocomplete="username" />
        </FieldRow>

        <FieldRow label="口令" for-id="password">
          <input
            id="password"
            v-model="form.password"
            type="password"
            autocomplete="current-password"
          />
        </FieldRow>

        <p v-if="error" class="plate__error" role="alert">{{ error }}</p>

        <Btn variant="solid" type="submit" :loading="busy">
          {{ busy ? '正在核对' : '登录' }}
        </Btn>
      </form>

      <p class="plate__foot">
        本系统数据为合成演示数据，不含任何真实师生信息。
      </p>
    </section>

    <aside class="dir">
      <h2 class="dir__title">演示账号</h2>
      <p class="dir__note">口令统一为 123456。点任意一行填入左侧表单。</p>
      <ul class="dir__list">
        <li v-for="a in accounts" :key="a.username">
          <button type="button" class="dir__row" @click="fill(a.username)">
            <span class="dir__no num">{{ a.username }}</span>
            <span class="dir__role">{{ a.role }}</span>
            <span class="dir__note-cell">{{ a.note }}</span>
          </button>
        </li>
      </ul>
      <p class="dir__foot">
        智能问答依据《福州大学至诚学院学生手册》作答，每条结论附带原文引用。
      </p>
    </aside>
  </div>
</template>

<style scoped>
.door {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  background: var(--ground);
}

.plate {
  background: var(--structure);
  color: var(--face);
  padding: var(--s-12) clamp(var(--s-6), 6vw, 88px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--s-4);
}

.plate__brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: var(--s-3);
  border-bottom: 1px solid var(--structure-line);
  margin-bottom: var(--s-4);
}

.plate__name {
  font-size: var(--t-md);
  font-weight: 600;
  letter-spacing: 0.08em;
}

.plate__sub {
  font-size: var(--t-xs);
  color: var(--muted);
}

.plate__title {
  font-size: var(--t-xl);
  font-weight: 700;
  letter-spacing: 0.03em;
  line-height: 1.2;
}

.plate__lead {
  font-size: var(--t-sm);
  color: var(--muted);
  max-width: 34ch;
}

.plate__form {
  display: flex;
  flex-direction: column;
  gap: var(--s-4);
  margin-top: var(--s-6);
  max-width: 340px;
}

.plate__form :deep(.field__label) {
  color: var(--muted);
}

.plate__form :deep(input) {
  background: var(--structure-deep);
  border-color: var(--structure-line);
  color: var(--face);
}

.plate__form :deep(input:hover) {
  border-color: var(--muted);
}

.plate__form :deep(.btn--solid) {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--face);
  font-weight: 600;
  height: var(--control-h);
}

.plate__form :deep(.btn--solid:hover:not(:disabled)) {
  background: var(--face);
  border-color: var(--face);
  /* 悬停换成浅底，字必须同时变深，否则浅底浅字什么也看不见 */
  color: var(--ink);
}

.plate__error {
  font-size: var(--t-sm);
  align-self: flex-start;
  background: var(--accent);
  color: var(--face);
  font-weight: 600;
  padding: 2px var(--s-3);
}

.plate__foot {
  margin-top: var(--s-8);
  font-size: var(--t-xs);
  color: var(--muted);
  max-width: 40ch;
}

.dir {
  padding: var(--s-12) clamp(var(--s-6), 6vw, 88px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--s-4);
  /* 目录放在内容面上：次要文字在 --ground 上对比度不足，在 --face 上刚好过 AA */
  background: var(--face);
}

.dir__title {
  font-size: var(--t-lg);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.dir__note {
  font-size: var(--t-sm);
  color: var(--ink-muted);
}

.dir__list {
  list-style: none;
  margin: var(--s-2) 0 0;
  padding: 0;
  border-top: 1px solid var(--line-strong);
}

.dir__row {
  width: 100%;
  display: grid;
  grid-template-columns: 88px 96px minmax(0, 1fr);
  align-items: baseline;
  gap: var(--s-3);
  padding: var(--s-3) var(--s-2);
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
  text-align: left;
  transition: background var(--dur) var(--ease);
}

.dir__row:hover {
  background: var(--face-raised);
}

.dir__no {
  font-size: var(--t-sm);
  font-weight: 600;
}

.dir__role {
  font-size: var(--t-xs);
  color: var(--accent-deep);
}

.dir__note-cell {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}

.dir__foot {
  margin-top: var(--s-4);
  font-size: var(--t-xs);
  color: var(--ink-muted);
  max-width: 44ch;
}

@media (max-width: 900px) {
  .door {
    grid-template-columns: 1fr;
  }

  .plate,
  .dir {
    padding: var(--s-8) var(--s-6);
  }

  .dir__row {
    grid-template-columns: 80px 88px minmax(0, 1fr);
  }
}
</style>
