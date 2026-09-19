<script setup lang="ts">
/**
 * 我的课表。学生与教师共用：数据来源不同，网格语言相同。
 *
 * 冲突处理是本页的技术核心：同一个星期、时间重叠的两门课不能叠在一起画，
 * 要把该时间段横向切成若干条泳道并排展示。分组用传递闭包（A 与 B 重叠、
 * B 与 C 重叠则三者同组），组内再贪心分配泳道。
 */
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { enrollmentApi, scheduleApi } from '@/api'
import type { ConflictItem, CreditSummary, TimetableEntry } from '@/api/types'
import { creditText, gpaText, teachingWeek, termWeekCount, weekdayText } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import { useCurrentTerm } from '@/components/useTerm'
import Plate from '@/components/Plate.vue'
import DataTable from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import type { Column } from '@/components/DataTable.vue'

const auth = useAuthStore()
const { currentTerm, load: loadTerm } = useCurrentTerm()

const entries = ref<TimetableEntry[]>([])
const conflicts = ref<ConflictItem[]>([])
const summary = ref<CreditSummary | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

const DAYS = [1, 2, 3, 4, 5, 6, 7]
const SECTIONS = 12
const ROW_H = 46

async function load() {
  state.value = 'loading'
  try {
    await loadTerm()
    const termId = currentTerm.value?.id
    const data = await scheduleApi.my(termId)
    entries.value = data.entries ?? []
    conflicts.value = auth.isStudent
      ? await enrollmentApi.conflicts(termId).catch(() => [])
      : []
    summary.value = auth.isStudent ? await enrollmentApi.summary().catch(() => null) : null
    state.value = entries.value.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '课表加载失败'
  }
}

onMounted(load)

interface Placed extends TimetableEntry {
  lane: number
  lanes: number
}

/** 把一天的课按重叠关系分组，再在组内分配泳道。 */
function layoutDay(day: number): Placed[] {
  const list = entries.value
    .filter((e) => e.weekday === day)
    .slice()
    .sort((a, b) => a.startSection - b.startSection || a.endSection - b.endSection)

  const placed: Placed[] = []
  let group: TimetableEntry[] = []
  let groupEnd = -1

  const settle = () => {
    if (!group.length) return
    const laneEnds: number[] = []
    const assigned = group.map((e) => {
      let lane = laneEnds.findIndex((end) => end < e.startSection)
      if (lane === -1) {
        laneEnds.push(e.endSection)
        lane = laneEnds.length - 1
      } else {
        laneEnds[lane] = e.endSection
      }
      return { entry: e, lane }
    })
    const lanes = laneEnds.length
    for (const a of assigned) placed.push({ ...a.entry, lane: a.lane, lanes })
    group = []
    groupEnd = -1
  }

  for (const e of list) {
    if (group.length && e.startSection > groupEnd) settle()
    group.push(e)
    groupEnd = Math.max(groupEnd, e.endSection)
  }
  settle()
  return placed
}

const grid = computed(() => DAYS.map((d) => ({ day: d, items: layoutDay(d) })))
const totalCredit = computed(() => entries.value.reduce((s, e) => s + (e.credit ?? 0), 0))
const week = computed(() => teachingWeek(currentTerm.value))
const weekCount = computed(() => termWeekCount(currentTerm.value))

const listColumns: Column[] = [
  { key: 'code', label: '课程代码', width: '120px' },
  { key: 'name', label: '课程名称' },
  { key: 'teacher', label: '任课教师', width: '110px' },
  { key: 'time', label: '上课时间', width: '200px' },
  { key: 'room', label: '地点', width: '130px' },
  { key: 'credit', label: '学分', width: '70px', align: 'right' },
]

const todayWeekday = new Date().getDay() === 0 ? 7 : new Date().getDay()
</script>

<template>
  <Plate
    :title="auth.isStudent ? '我的课表' : '我的授课安排'"
    :note="
      state === 'ready'
        ? `${currentTerm?.name ?? ''}，共 ${entries.length} 项，合计 ${creditText(totalCredit)} 学分`
        : (currentTerm?.name ?? '')
    "
  >
    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="本学期还没有课"
      :empty-detail="
        auth.isStudent
          ? '选课之后课表会自动出现在这里，可以先去选课页看看有哪些课。'
          : '教务尚未为你安排本学期的教学班。'
      "
      :skeleton-rows="6"
      @retry="load"
    >
      <div
        class="ruler"
        role="img"
        :aria-label="week ? `教学周进度，当前第 ${week} 周` : '当前不在教学周内'"
      >
        <span class="ruler__label">教学周</span>
        <ol class="ruler__cells">
          <li
            v-for="w in weekCount"
            :key="w"
            class="ruler__cell num"
            :class="{ 'is-past': week && w < week, 'is-now': week === w }"
          >
            {{ w }}
          </li>
        </ol>
        <span v-if="!week" class="ruler__off">当前不在教学周内</span>
      </div>

      <!-- 契约的第一屏承诺包含「一眼看到学分缺口」，这块台账就是那一句的落点 -->
      <dl v-if="summary" class="ledger" aria-label="学分台账">
        <div class="ledger__cell">
          <dt>已获学分</dt>
          <dd class="num">{{ creditText(summary.earnedCredit) }}</dd>
        </div>
        <div class="ledger__cell">
          <dt>在修学分</dt>
          <dd class="num">{{ creditText(summary.inProgressCredit) }}</dd>
        </div>
        <div class="ledger__cell">
          <dt>平均学分绩点</dt>
          <dd class="num">{{ gpaText(summary.gpa) }}</dd>
        </div>
        <div class="ledger__cell">
          <dt>已通过课程</dt>
          <dd class="num">{{ summary.passedCourses }}</dd>
        </div>
        <RouterLink class="ledger__more" to="/me/grades">看全部成绩</RouterLink>
      </dl>

      <div v-if="conflicts.length" class="alert">
        <p class="alert__title">选课里有 {{ conflicts.length }} 处时间冲突</p>
        <ul class="alert__list">
          <li v-for="(c, i) in conflicts" :key="i">
            {{ c.courseA }}（{{ c.timeA }}）与 {{ c.courseB }}（{{ c.timeB }}）
          </li>
        </ul>
      </div>

      <div class="grid" :style="{ '--row-h': `${ROW_H}px` }">
        <div class="grid__corner"></div>
        <div
          v-for="d in DAYS"
          :key="`h${d}`"
          class="grid__head"
          :class="{ 'is-today': d === todayWeekday }"
        >
          {{ weekdayText(d) }}
        </div>

        <div class="grid__axis">
          <div v-for="s in SECTIONS" :key="`s${s}`" class="grid__axis-cell num">{{ s }}</div>
        </div>

        <div v-for="col in grid" :key="`c${col.day}`" class="grid__day">
          <div v-for="s in SECTIONS" :key="`r${s}`" class="grid__row"></div>
          <article
            v-for="item in col.items"
            :key="`${item.courseCode}-${item.className}-${item.startSection}`"
            class="block"
            :style="{
              top: `${(item.startSection - 1) * ROW_H}px`,
              height: `${(item.endSection - item.startSection + 1) * ROW_H - 6}px`,
              left: `calc(${(item.lane / item.lanes) * 100}% + 2px)`,
              width: `calc(${100 / item.lanes}% - 4px)`,
            }"
          >
            <p class="block__code">{{ item.courseCode }}</p>
            <p class="block__name">{{ item.courseName }}</p>
            <p class="block__meta">
              {{ item.classroom || '地点待定' }}
              <template v-if="item.teacherName">，{{ item.teacherName }}</template>
              <template v-else-if="item.className">，{{ item.className }}</template>
            </p>
          </article>
        </div>
      </div>

      <div class="listing">
        <p class="listing__title">按课程列出</p>
        <DataTable :columns="listColumns" state="ready" min-width="760px">
          <tr v-for="e in entries" :key="`${e.courseCode}-${e.timeText}`">
            <td><span class="num">{{ e.courseCode }}</span></td>
            <td>{{ e.courseName }}</td>
            <td>{{ e.teacherName ?? e.className ?? '-' }}</td>
            <td>{{ e.timeText }}</td>
            <td>{{ e.classroom || '-' }}</td>
            <td class="num num-end">{{ creditText(e.credit) }}</td>
          </tr>
        </DataTable>
      </div>
    </StateHost>
  </Plate>
</template>

<style scoped>
.ruler {
  display: flex;
  align-items: center;
  gap: var(--s-3);
  padding: var(--s-3) var(--s-4) 0;
}

.ruler__label {
  font-size: var(--t-xs);
  color: var(--ink-muted);
  white-space: nowrap;
}

.ruler__cells {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(0, 1fr);
  gap: 2px;
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
}

.ruler__cell {
  font-size: 10px;
  text-align: center;
  padding: 1px 0;
  background: var(--ground);
  color: var(--ink-muted);
}

.ruler__cell.is-past {
  background: var(--line);
  color: var(--ink);
}

.ruler__cell.is-now {
  background: var(--accent);
  color: var(--face);
  font-weight: 700;
}

.ruler__off {
  font-size: var(--t-xs);
  color: var(--accent-deep);
  white-space: nowrap;
}

.ledger {
  display: flex;
  align-items: stretch;
  margin: 0 var(--s-4);
  border: 1px solid var(--line);
  background: var(--face);
}

.ledger__cell {
  flex: 1;
  padding: var(--s-3) var(--s-4);
  border-right: 1px solid var(--line);
}

.ledger__cell dt {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}

.ledger__cell dd {
  margin: 2px 0 0;
  font-size: var(--t-lg);
  font-weight: 600;
  line-height: 1.1;
}

.ledger__more {
  display: flex;
  align-items: center;
  padding: 0 var(--s-4);
  font-size: var(--t-sm);
  color: var(--accent-deep);
  white-space: nowrap;
}

.alert {
  margin: 0 var(--s-4);
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

.grid {
  display: grid;
  grid-template-columns: 44px repeat(7, minmax(0, 1fr));
  padding: 0 var(--s-4);
}

.grid__corner {
  border-bottom: 1px solid var(--line-strong);
}

.grid__head {
  padding: var(--s-2);
  font-size: var(--t-xs);
  font-weight: 600;
  color: var(--ink-muted);
  text-align: center;
  border-bottom: 1px solid var(--line-strong);
}

.grid__head.is-today {
  color: var(--accent-deep);
  box-shadow: inset 0 -2px 0 var(--accent);
}

.grid__axis {
  display: grid;
  grid-template-rows: repeat(12, var(--row-h));
}

.grid__axis-cell {
  font-size: 10px;
  color: var(--ink-muted);
  text-align: right;
  padding-right: var(--s-2);
  border-right: 1px solid var(--line);
}

.grid__day {
  position: relative;
  border-right: 1px solid var(--line);
}

.grid__day:last-child {
  border-right: 0;
}

.grid__row {
  height: var(--row-h);
  border-bottom: 1px solid var(--line);
}

.block {
  position: absolute;
  overflow: hidden;
  background: var(--face);
  border: 1px solid var(--line-strong);
  padding: var(--s-1) var(--s-2);
}

/* 课程代码做成一块小铭牌，这是这个世界表达身份的方式 */
.block__code {
  align-self: flex-start;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--face);
  background: var(--structure);
  padding: 1px 4px;
  letter-spacing: 0.02em;
}

.block__name {
  font-size: var(--t-xs);
  font-weight: 600;
  line-height: 1.25;
}

.block__meta {
  font-size: 10px;
  color: var(--ink-muted);
  line-height: 1.3;
}

.listing {
  padding: var(--s-4);
  border-top: 1px solid var(--line);
}

.listing__title {
  font-size: var(--t-sm);
  font-weight: 600;
  margin-bottom: var(--s-2);
}

@media (max-width: 1100px) {
  .grid {
    overflow-x: auto;
    grid-template-columns: 40px repeat(7, 118px);
  }
}

@media (max-width: 900px) {
  /* 台账在窄屏折成两列，避免 flex 的最小内容宽度把页面撑宽 */
  .ledger {
    flex-wrap: wrap;
  }

  .ledger__cell {
    flex: 1 1 50%;
    min-width: 0;
  }

  .ledger__cell:nth-child(2) {
    border-right: 0;
  }

  .ledger__cell:nth-child(-n + 2) {
    border-bottom: 1px solid var(--line);
  }

  .ledger__more {
    flex: 1 1 100%;
    padding: var(--s-2) var(--s-4);
    border-top: 1px solid var(--line);
  }
}
</style>
