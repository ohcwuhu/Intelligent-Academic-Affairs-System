<script setup lang="ts">
/**
 * 课表网格。学生课表、教师课表、专业课程表共用同一套画法。
 *
 * 冲突处理是这里的核心：同一个星期、时间重叠的两门课不能叠在一起画，
 * 要把该时间段横向切成若干条泳道并排展示。分组用传递闭包
 * （A 与 B 重叠、B 与 C 重叠则三者同组），组内再贪心分配泳道。
 */
import { computed } from 'vue'
import type { TimetableEntry } from '@/api/types'
import { weekdayText } from '@/utils/format'

const props = defineProps<{ entries: TimetableEntry[] }>()

const DAYS = [1, 2, 3, 4, 5, 6, 7]
const SECTIONS = 12
const ROW_H = 46
const todayWeekday = new Date().getDay() === 0 ? 7 : new Date().getDay()

interface Placed extends TimetableEntry {
  lane: number
  lanes: number
}

/** 把一天的课按重叠关系分组，再在组内分配泳道。 */
function layoutDay(day: number): Placed[] {
  const list = props.entries
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
</script>

<template>
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
</template>

<style scoped>
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

@media (max-width: 1100px) {
  .grid {
    overflow-x: auto;
    grid-template-columns: 40px repeat(7, 118px);
  }
}
</style>
