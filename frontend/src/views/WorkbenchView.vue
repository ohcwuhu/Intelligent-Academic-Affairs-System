<script setup lang="ts">
/**
 * 教务工作台。
 *
 * 教务的一天是"先看今天有什么要办的"，而不是"先去某个档案列表"。
 * 这一页把散在各页的待办与质量数字收到一起，并且每个数字都能点进对应的处理页面——
 * 看得到、点得动，工作台才有意义。
 */
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { workbenchApi } from '@/api'
import type { Workbench } from '@/api/types'
import Plate from '@/components/Plate.vue'
import StateHost from '@/components/StateHost.vue'

const data = ref<Workbench | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

async function load() {
  state.value = 'loading'
  try {
    data.value = await workbenchApi.load()
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '工作台加载失败'
  }
}
onMounted(load)
</script>

<template>
  <StateHost :state="state" :error-detail="errorDetail" :skeleton-rows="6" @retry="load">
    <template v-if="data">
      <Plate :title="`今天要办的（${data.termName ?? '学期未设置'}）`">
        <dl class="ledger">
          <div class="ledger__cell">
            <dt>待审申请</dt>
            <dd class="num">{{ data.pendingApplications }}</dd>
            <RouterLink class="ledger__go" to="/admin/applications">去审批</RouterLink>
          </div>
          <div class="ledger__cell">
            <dt>待回复留言</dt>
            <dd class="num">{{ data.waitingMessages }}</dd>
            <RouterLink class="ledger__go" to="/info">去回复</RouterLink>
          </div>
          <div class="ledger__cell">
            <dt>待处理反馈</dt>
            <dd class="num">{{ data.pendingFeedback }}</dd>
            <RouterLink class="ledger__go" to="/admin/governance">去处理</RouterLink>
          </div>
          <div class="ledger__cell">
            <dt>待处理知识缺口</dt>
            <dd class="num">{{ data.pendingGaps }}</dd>
            <RouterLink class="ledger__go" to="/admin/governance">去补录</RouterLink>
          </div>
        </dl>

        <template #actions>
          <RouterLink class="quick" to="/admin/classes">新增开课</RouterLink>
          <RouterLink class="quick" to="/admin/import">导入数据</RouterLink>
          <RouterLink class="quick" to="/info">发布通知</RouterLink>
        </template>

        <div v-if="data.todos.length" class="todo">
          <p class="todo__title">待审申请（前 {{ data.todos.length }} 条）</p>
          <ul>
            <li v-for="t in data.todos" :key="`${t.kind}-${t.id}`" class="todo__row">
              <span class="todo__head">{{ t.title }}</span>
              <span class="todo__detail">{{ t.detail }}</span>
              <span class="todo__time num">{{ t.createdAt }}</span>
            </li>
          </ul>
        </div>
        <p v-else class="todo__none">没有待审申请。</p>

        <div v-if="data.waitingList.length" class="todo">
          <p class="todo__title">等回复的留言（前 {{ data.waitingList.length }} 条）</p>
          <ul>
            <li v-for="m in data.waitingList" :key="m.id" class="todo__row">
              <span class="todo__head">{{ m.studentName }}（{{ m.studentNo }}）</span>
              <span class="todo__detail">{{ m.content }}</span>
              <span class="todo__time num">{{ m.createdAt }}</span>
            </li>
          </ul>
        </div>
      </Plate>

      <Plate title="今日问答质量" note="越界与注入会被拦下并计入拦截数；平均耗时只统计今日提问">
        <dl class="ledger ledger--tight">
          <div class="ledger__cell"><dt>提问</dt><dd class="num">{{ data.askToday }}</dd></div>
          <div class="ledger__cell"><dt>拦截</dt><dd class="num">{{ data.blockedToday }}</dd></div>
          <div class="ledger__cell"><dt>注入尝试</dt><dd class="num">{{ data.injectionToday }}</dd></div>
          <div class="ledger__cell"><dt>平均耗时</dt><dd class="num">{{ data.avgDurationMs }}ms</dd></div>
        </dl>
        <p class="foot">
          问答明细与审计日志在 <RouterLink to="/admin/governance">反馈与缺口</RouterLink> 页；
          语料与发布门禁在 <RouterLink to="/admin/knowledge">知识库治理</RouterLink> 页。
        </p>
      </Plate>

      <Plate title="数据规模" note="决定审核能不能给数的是培养方案：没有它，毕业学分缺口算不出来">
        <dl class="ledger ledger--tight">
          <div class="ledger__cell"><dt>在校学生</dt><dd class="num">{{ data.students }}</dd></div>
          <div class="ledger__cell"><dt>教师</dt><dd class="num">{{ data.teachers }}</dd></div>
          <div class="ledger__cell"><dt>课程库</dt><dd class="num">{{ data.courses }}</dd></div>
          <div class="ledger__cell"><dt>本学期教学班</dt><dd class="num">{{ data.teachingClasses }}</dd></div>
          <div class="ledger__cell"><dt>培养方案</dt><dd class="num">{{ data.programs }}</dd></div>
          <div class="ledger__cell"><dt>知识库切片</dt><dd class="num">{{ data.chunks }}</dd></div>
          <div class="ledger__cell">
            <dt>90 天内到期文档</dt>
            <dd class="num" :class="{ 'is-warn': data.expiringDocuments > 0 }">{{ data.expiringDocuments }}</dd>
          </div>
        </dl>
        <p class="foot">学分与绩点由服务端计算，界面不做折算；当前学期学分要求见「培养方案」页。</p>
      </Plate>
    </template>
  </StateHost>
</template>

<style scoped>
.ledger {
  display: flex;
  flex-wrap: wrap;
  margin: 0 var(--s-4) var(--s-4);
  border: 1px solid var(--line);
  background: var(--face);
}
.ledger:first-child {
  margin-top: var(--s-4);
}
.ledger--tight {
  margin-bottom: 0;
}
.ledger__cell {
  flex: 1 1 160px;
  padding: var(--s-3) var(--s-4);
  border-right: 1px solid var(--line);
}
.ledger__cell:last-child {
  border-right: 0;
}
.ledger__cell dt {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.ledger__cell dd {
  margin: 2px 0 0;
  font-size: var(--t-xl);
  font-weight: 600;
  line-height: 1.1;
}
.ledger__cell dd.is-warn {
  color: var(--accent-deep);
}
.ledger__go {
  display: inline-block;
  margin-top: var(--s-2);
  font-size: var(--t-sm);
  color: var(--accent-deep);
}
.quick {
  padding: 0 var(--s-2);
  font-size: var(--t-sm);
  color: var(--accent-deep);
}
.todo {
  border-top: 1px solid var(--line);
}
.todo__title {
  padding: var(--s-3) var(--s-4) var(--s-1);
  font-size: var(--t-sm);
  font-weight: 600;
}
.todo ul {
  list-style: none;
  margin: 0;
  padding: 0;
}
.todo__row {
  display: flex;
  gap: var(--s-4);
  align-items: baseline;
  padding: var(--s-2) var(--s-4);
  border-top: 1px solid var(--line);
  font-size: var(--t-sm);
}
.todo__head {
  flex: 0 0 auto;
  font-weight: 600;
}
.todo__detail {
  flex: 1;
  color: var(--ink-muted);
}
.todo__time {
  flex: 0 0 auto;
  color: var(--ink-muted);
  font-size: var(--t-xs);
}
.todo__none {
  padding: var(--s-3) var(--s-4);
  border-top: 1px solid var(--line);
  font-size: var(--t-sm);
  color: var(--ink-muted);
}
.foot {
  padding: var(--s-3) var(--s-4) var(--s-4);
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
</style>
