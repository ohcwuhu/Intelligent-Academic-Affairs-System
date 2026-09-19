<script setup lang="ts">
/** 我的档案。学生只能看到本人档案，这一点由服务端保证。 */
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { studentApi } from '@/api'
import type { StudentVO } from '@/api/types'
import Plate from '@/components/Plate.vue'
import StateHost from '@/components/StateHost.vue'

const me = ref<StudentVO | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

async function load() {
  state.value = 'loading'
  try {
    me.value = await studentApi.me()
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '档案加载失败'
  }
}

onMounted(load)
</script>

<template>
  <Plate title="我的档案" note="学籍信息由教务处维护，如有出入请联系所在院系教学办">
    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="没有查到档案"
      empty-detail="请联系教务处确认学籍信息是否已录入。"
      :skeleton-rows="5"
      @retry="load"
    >
      <dl v-if="me" class="sheet">
        <div class="sheet__row"><dt>学号</dt><dd class="num">{{ me.studentNo }}</dd></div>
        <div class="sheet__row"><dt>姓名</dt><dd>{{ me.name }}</dd></div>
        <div class="sheet__row"><dt>性别</dt><dd>{{ me.gender }}</dd></div>
        <div class="sheet__row"><dt>出生日期</dt><dd class="num">{{ me.birthDate ?? '未登记' }}</dd></div>
        <div class="sheet__row"><dt>年级</dt><dd class="num">{{ me.grade }}</dd></div>
        <div class="sheet__row"><dt>学院</dt><dd>{{ me.collegeName }}</dd></div>
        <div class="sheet__row"><dt>专业</dt><dd>{{ me.majorName }}</dd></div>
        <div class="sheet__row"><dt>班级</dt><dd>{{ me.clazzName }}</dd></div>
        <div class="sheet__row"><dt>学籍状态</dt><dd>{{ me.status }}</dd></div>
        <div class="sheet__row"><dt>联系电话</dt><dd class="num">{{ me.phone ?? '未登记' }}</dd></div>
        <div class="sheet__row"><dt>电子邮箱</dt><dd class="num">{{ me.email ?? '未登记' }}</dd></div>
      </dl>
    </StateHost>
  </Plate>
</template>

<style scoped>
.sheet {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.sheet__row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: var(--s-3);
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
}

.sheet__row:nth-child(odd) {
  border-right: 1px solid var(--line);
}

.sheet__row dt {
  font-size: var(--t-xs);
  color: var(--ink-muted);
  padding-top: 2px;
}

.sheet__row dd {
  margin: 0;
  font-size: var(--t-sm);
}

@media (max-width: 900px) {
  .sheet {
    grid-template-columns: 1fr;
  }
  .sheet__row:nth-child(odd) {
    border-right: 0;
  }
}
</style>
