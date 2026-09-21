<script setup lang="ts">
/**
 * 培养计划与毕业审核。
 *
 * 学生最想知道的就一句话："我还差多少学分能毕业"。
 * 所以这一页先给结论（总缺口 + 是否修满），再给模块账目，
 * 最后才是计划课程清单。数字全部由服务端按培养方案算，界面不做推算。
 */
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { programApi } from '@/api'
import type { ProgramAudit } from '@/api/types'
import { creditText } from '@/utils/format'
import Plate from '@/components/Plate.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'

const audit = ref<ProgramAudit | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

async function load() {
  state.value = 'loading'
  try {
    audit.value = await programApi.audit()
    state.value = audit.value.programId ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '培养计划加载失败'
  }
}
onMounted(load)

const moduleColumns: Column[] = [
  { key: 'category', label: '课程模块' },
  { key: 'required', label: '要求学分', width: '100px', align: 'right' },
  { key: 'earned', label: '已获学分', width: '100px', align: 'right' },
  { key: 'gap', label: '还差', width: '90px', align: 'right' },
  { key: 'progress', label: '完成情况', width: '150px' },
  { key: 'missing', label: '还没通过的课' },
]

const percent = computed(() => {
  const a = audit.value
  if (!a?.minCredit || a.earned == null) return 0
  return Math.min(100, Math.round((a.earned / a.minCredit) * 100))
})
</script>

<template>
  <Plate
    title="培养计划与毕业审核"
    :note="audit?.programTitle ?? '按本专业现行培养方案逐模块核对'"
  >
    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="还没有你这个专业的培养方案"
      empty-detail="教务把培养方案导入后，这里会显示学分缺口。"
      :skeleton-rows="6"
      @retry="load"
    >
      <template v-if="audit">
        <div class="summary">
          <div class="summary__main">
            <p class="summary__label">毕业最低学分</p>
            <p class="summary__value num">{{ creditText(audit.minCredit) }}</p>
          </div>
          <div class="summary__main">
            <p class="summary__label">已获学分</p>
            <p class="summary__value num">{{ creditText(audit.earned) }}</p>
          </div>
          <div class="summary__main" :class="{ 'is-ok': audit.complete }">
            <p class="summary__label">还差</p>
            <p class="summary__value num">{{ creditText(audit.gap) }}</p>
          </div>
          <div class="summary__bar">
            <span class="summary__fill" :style="{ width: percent + '%' }"></span>
            <span class="summary__percent num">{{ percent }}%</span>
          </div>
        </div>

        <dl class="ledger">
          <div><dt>专业</dt><dd>{{ audit.majorName ?? '—' }}</dd></div>
          <div><dt>结论</dt><dd>{{ audit.complete ? '按现行方案学分已修满' : '未修满，见下方模块账目' }}</dd></div>
        </dl>

        <DataTable :columns="moduleColumns" state="ready" min-width="980px">
          <tr v-for="m in audit.modules" :key="m.category">
            <td>{{ m.category }}</td>
            <td class="num num-end">{{ creditText(m.required) }}</td>
            <td class="num num-end">{{ creditText(m.earned) }}</td>
            <td class="num num-end">{{ creditText(m.gap) }}</td>
            <td>
              <span class="bar"><span class="bar__fill" :style="{ width: Math.min(100, m.required ? (m.earned / m.required) * 100 : 0) + '%' }"></span></span>
              <span class="dim num">{{ m.passedCourses }}/{{ m.planCourses }}</span>
            </td>
            <td class="dim">
              {{ m.missing.length ? m.missing.join('、') : '—' }}
            </td>
          </tr>
        </DataTable>

        <section v-if="audit.passedOutsidePlan.length" class="outside">
          <p class="outside__title">
            已通过但不在本方案计划内的课（{{ audit.passedOutsidePlan.length }} 门，不计入上面模块）
          </p>
          <ul>
            <li v-for="c in audit.passedOutsidePlan" :key="c.courseName + c.note">
              {{ c.courseName }}　<span class="num">{{ creditText(c.credit) }}</span> 学分　
              <span class="dim">{{ c.note }}</span>
            </li>
          </ul>
        </section>

        <ul class="notes">
          <li v-for="(n, i) in audit.notes" :key="i">{{ n }}</li>
        </ul>
      </template>
    </StateHost>
  </Plate>
</template>

<style scoped>
.summary {
  display: flex;
  align-items: stretch;
  gap: var(--s-4);
  padding: var(--s-4);
  border-bottom: 1px solid var(--line);
}
.summary__main {
  min-width: 120px;
}
.summary__main.is-ok .summary__value {
  color: var(--ok);
}
.summary__label {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.summary__value {
  font-size: var(--t-xl);
  font-weight: 600;
  line-height: 1.1;
}
.summary__bar {
  position: relative;
  flex: 1;
  align-self: center;
  height: 14px;
  background: var(--ground);
  border: 1px solid var(--line);
}
.summary__fill {
  display: block;
  height: 100%;
  background: var(--structure);
}
.summary__percent {
  position: absolute;
  right: var(--s-2);
  top: -2px;
  font-size: var(--t-xs);
  color: var(--ink);
}
.ledger {
  display: flex;
  gap: var(--s-8);
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
  background: var(--face);
}
.ledger dt {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.ledger dd {
  margin: 2px 0 0;
  font-size: var(--t-sm);
}
.bar {
  display: inline-block;
  width: 90px;
  height: 8px;
  margin-right: var(--s-2);
  background: var(--ground);
  border: 1px solid var(--line);
  vertical-align: middle;
}
.bar__fill {
  display: block;
  height: 100%;
  background: var(--accent);
}
.outside {
  padding: var(--s-3) var(--s-4);
  border-top: 1px solid var(--line);
}
.outside__title {
  font-size: var(--t-sm);
  font-weight: 600;
  margin-bottom: var(--s-2);
}
.outside ul {
  list-style: none;
  padding: 0;
  font-size: var(--t-sm);
}
.notes {
  padding: var(--s-3) var(--s-4) var(--s-4) calc(var(--s-4) + 1.2em);
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.dim {
  color: var(--ink-muted);
  font-size: var(--t-xs);
}
</style>
