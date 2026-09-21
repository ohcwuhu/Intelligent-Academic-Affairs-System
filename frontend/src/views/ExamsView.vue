<script setup lang="ts">
/**
 * 我的考试。
 *
 * 学生要的其实只有三件事：什么时候考、在哪考、还有几天。
 * 所以列表按时间排，天数直接算好写出来；两场考试撞在一起时给一条提示，
 * 这属于学生自己处理不了的情况，必须显式说出来。
 */
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { examApi } from '@/api'
import type { ExamRow } from '@/api/types'
import { useCurrentTerm } from '@/components/useTerm'
import Plate from '@/components/Plate.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'

const { currentTerm, load: loadTerm } = useCurrentTerm()
const rows = ref<ExamRow[]>([])
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

const columns: Column[] = [
  { key: 'date', label: '日期', width: '120px' },
  { key: 'time', label: '时间', width: '150px' },
  { key: 'course', label: '课程' },
  { key: 'type', label: '类型', width: '90px' },
  { key: 'room', label: '考场', width: '130px' },
  { key: 'seat', label: '座位', width: '80px' },
  { key: 'left', label: '距考试', width: '100px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    await loadTerm()
    rows.value = await examApi.my(currentTerm.value?.id)
    state.value = rows.value.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '考试安排加载失败'
  }
}
onMounted(load)

const clashes = computed(() => rows.value.filter((r) => (r.conflictWith?.length ?? 0) > 0))
const next = computed(() => rows.value.find((r) => (r.daysAhead ?? -1) >= 0) ?? null)

function leftText(days: number | null): string {
  if (days == null) return '—'
  if (days < 0) return '已结束'
  if (days === 0) return '今天'
  return `${days} 天后`
}
</script>

<template>
  <Plate
    title="我的考试"
    :note="
      currentTerm
        ? `${currentTerm.name}，共 ${rows.length} 场`
        : ''
    "
  >
    <p v-if="next" class="next">
      最近一场：<strong>{{ next.courseName }}</strong>
      {{ next.examDate }} {{ next.startTime }}-{{ next.endTime }}，
      {{ next.classroom || '考场待定' }}
      <span class="next__left">{{ leftText(next.daysAhead) }}</span>
    </p>

    <div v-if="clashes.length" class="alert" role="alert">
      <p class="alert__title">有 {{ clashes.length }} 场考试时间重叠</p>
      <ul class="alert__list">
        <li v-for="c in clashes" :key="c.id">
          {{ c.examDate }} {{ c.startTime }}-{{ c.endTime }}：{{ c.courseName }} 与
          {{ c.conflictWith.join('、') }}
        </li>
      </ul>
      <p class="alert__foot">这种情况请尽快联系教务处，不要自行决定考哪一场。</p>
    </div>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="本学期还没有考试安排"
      empty-detail="教务排好考试后会出现在这里；也可以先去问一下智能问答里的考核规定。"
      :skeleton-rows="5"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="900px">
        <tr v-for="r in rows" :key="r.id">
          <td class="num">{{ r.examDate }}</td>
          <td class="num">{{ r.startTime }}-{{ r.endTime }}</td>
          <td>
            {{ r.courseName }}
            <span class="dim">（{{ r.courseCode }}）</span>
            <template v-if="r.teacherName">
              <br /><span class="dim">{{ r.teacherName }}</span>
            </template>
          </td>
          <td>{{ r.examType }}</td>
          <td>{{ r.classroom || '待定' }}</td>
          <td class="num">{{ r.seatNo || '—' }}</td>
          <td class="num num-end">{{ leftText(r.daysAhead) }}</td>
        </tr>
      </DataTable>
    </StateHost>
  </Plate>
</template>

<style scoped>
.next {
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
  background: var(--face);
  font-size: var(--t-sm);
}

.next__left {
  margin-left: var(--s-2);
  padding: 0 6px;
  background: var(--structure);
  color: var(--face);
  font-size: var(--t-xs);
}

.alert {
  margin: var(--s-4) var(--s-4) 0;
  border: 1px solid var(--accent);
  padding: var(--s-3);
}

.alert__title {
  font-size: var(--t-sm);
  font-weight: 600;
  color: var(--accent-deep);
}

.alert__list {
  margin: var(--s-2) 0 0;
  padding-left: 1.2em;
  font-size: var(--t-xs);
  color: var(--ink-muted);
}

.alert__foot {
  margin-top: var(--s-2);
  font-size: var(--t-xs);
  color: var(--accent-deep);
}
</style>
