<script setup lang="ts">
/**
 * 专业课程表。
 *
 * 与「我的课表」的区别在数据来源：那是我选了什么，这是这个专业开了什么。
 * 所以它同时是选课前的参考——先看这个专业这一学期开哪些课、什么时段，
 * 再决定怎么选。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ApiError } from '@/api/client'
import { basicApi, scheduleApi, studentApi } from '@/api'
import type { Major, MajorTimetable, Term } from '@/api/types'
import { creditText } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import Plate from '@/components/Plate.vue'
import FieldRow from '@/components/FieldRow.vue'
import StateHost from '@/components/StateHost.vue'
import TimetableGrid from '@/components/TimetableGrid.vue'

const majors = ref<Major[]>([])
const auth = useAuthStore()
const terms = ref<Term[]>([])
const data = ref<MajorTimetable | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

const grade = ref<number | null>(null)
const majorId = ref<number | null>(null)
const termId = ref<number | null>(null)

const GRADES = [2021, 2022, 2023, 2024, 2025]

async function init() {
  try {
    const [ms, ts] = await Promise.all([basicApi.majors(), basicApi.terms()])
    majors.value = ms
    terms.value = ts
    termId.value = ts.find((t) => t.isCurrent === 1)?.id ?? ts[0]?.id ?? null
    // 学生进来默认看自己的专业与年级，而不是列表里的第一个专业——
    // 后者会给出一个跟自己毫无关系的空课表，还得让人自己猜该选哪个
    if (auth.isStudent) {
      const me = await studentApi.me().catch(() => null)
      majorId.value = me?.majorId ?? ms[0]?.id ?? null
      grade.value = me?.grade ?? null
    } else {
      majorId.value = ms[0]?.id ?? null
    }
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '基础数据加载失败'
  }
}

async function load() {
  state.value = 'loading'
  try {
    data.value = await scheduleApi.major({
      majorId: majorId.value ?? undefined,
      grade: grade.value ?? undefined,
      termId: termId.value ?? undefined,
    })
    state.value = data.value.entries.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '专业课表加载失败'
  }
}

onMounted(async () => {
  await init()
  await load()
})
watch([majorId, grade, termId], load)

const totalCredit = computed(() =>
  (data.value?.entries ?? []).reduce((s, e) => s + (e.credit ?? 0), 0),
)
</script>

<template>
  <Plate
    title="专业课程表"
    :note="
      data
        ? `${data.termName ?? ''}，${data.majorName ?? '全校'}${data.grade ? ' ' + data.grade + ' 级' : ''}：` +
          `${data.courseCount} 门课，合计 ${creditText(totalCredit)} 学分`
        : '按专业与年级查看这一学期开出的课'
    "
  >
    <div class="filters">
      <FieldRow label="专业" for-id="major">
        <select id="major" v-model.number="majorId">
          <option :value="null">不限（全校）</option>
          <option v-for="m in majors" :key="m.id" :value="m.id">
            {{ m.code }} {{ m.name }}
          </option>
        </select>
      </FieldRow>
      <FieldRow label="年级" for-id="grade">
        <select id="grade" v-model.number="grade">
          <option :value="null">不限</option>
          <option v-for="g in GRADES" :key="g" :value="g">{{ g }} 级</option>
        </select>
      </FieldRow>
      <FieldRow label="学期" for-id="term">
        <select id="term" v-model.number="termId">
          <option v-for="t in terms" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
      </FieldRow>
    </div>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="这个专业这一学期还没有开课"
      empty-detail="换个年级或学期看看；专业课程表只收录面向该专业开出的教学班。"
      :skeleton-rows="6"
      @retry="load"
    >
      <TimetableGrid :entries="data?.entries ?? []" />

      <div class="listing">
        <p class="listing__title">按课程列出</p>
        <ul class="listing__items">
          <li v-for="e in data?.entries ?? []" :key="`${e.courseCode}-${e.timeText}`">
            <span class="num listing__code">{{ e.courseCode }}</span>
            <span class="listing__name">{{ e.courseName }}</span>
            <span class="listing__time">{{ e.timeText }}</span>
            <span class="listing__room">{{ e.classroom || '地点待定' }}</span>
            <span class="listing__teacher">{{ e.teacherName ?? '教师待定' }}</span>
          </li>
        </ul>
      </div>
    </StateHost>
  </Plate>
</template>

<style scoped>
.filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--s-3);
  padding: var(--s-4);
  border-bottom: 1px solid var(--line);
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

.listing__items {
  list-style: none;
  margin: 0;
  padding: 0;
}

.listing__items li {
  display: flex;
  align-items: baseline;
  gap: var(--s-4);
  padding: var(--s-2) 0;
  border-bottom: 1px solid var(--line);
  font-size: var(--t-sm);
}

.listing__items li:last-child {
  border-bottom: 0;
}

.listing__code {
  flex: none;
  padding: 0 6px;
  background: var(--structure);
  color: var(--face);
  font-size: var(--t-xs);
}

.listing__name {
  flex: 1;
}

.listing__time,
.listing__room,
.listing__teacher {
  color: var(--ink-muted);
  font-size: var(--t-xs);
}
</style>
