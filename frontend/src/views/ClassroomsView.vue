<script setup lang="ts">
/**
 * 教室使用情况。
 *
 * 数据就是排课结果：教室有没有被占，取决于有没有课排在它。
 * 学生找自习室、教师找调课后的空教室、教务看整体占用，看的是同一份东西。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ApiError } from '@/api/client'
import { classroomApi } from '@/api'
import type { ClassroomOccupancy, ClassroomSlot } from '@/api/types'
import { useCurrentTerm } from '@/components/useTerm'
import Plate from '@/components/Plate.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import FieldRow from '@/components/FieldRow.vue'

const { currentTerm, load: loadTerm } = useCurrentTerm()
const usage = ref<ClassroomOccupancy[]>([])
const slot = ref<ClassroomSlot | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const weekday = ref(1)
const startSection = ref(1)
const endSection = ref(2)

const WEEKDAYS = [
  { value: 1, text: '周一' },
  { value: 2, text: '周二' },
  { value: 3, text: '周三' },
  { value: 4, text: '周四' },
  { value: 5, text: '周五' },
  { value: 6, text: '周六' },
  { value: 7, text: '周日' },
]
const SECTIONS = Array.from({ length: 12 }, (_, i) => i + 1)

const columns: Column[] = [
  { key: 'room', label: '教室', width: '130px' },
  { key: 'when', label: '时间', width: '170px' },
  { key: 'course', label: '课程' },
  { key: 'class', label: '教学班', width: '200px' },
  { key: 'teacher', label: '教师', width: '100px' },
]

async function load() {
  state.value = 'loading'
  try {
    await loadTerm()
    const termId = currentTerm.value?.id
    usage.value = await classroomApi.usage(termId)
    await loadSlot()
    state.value = usage.value.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '教室使用情况加载失败'
  }
}

async function loadSlot() {
  if (endSection.value < startSection.value) endSection.value = startSection.value
  slot.value = await classroomApi.slot({
    termId: currentTerm.value?.id,
    weekday: weekday.value,
    startSection: startSection.value,
    endSection: endSection.value,
  })
}

onMounted(load)
watch([weekday, startSection, endSection], () => {
  void loadSlot().catch(() => {})
})

const roomCount = computed(() => new Set(usage.value.map((u) => u.classroom)).size)
</script>

<template>
  <Plate
    title="教室使用情况"
    :note="
      state === 'ready'
        ? `${currentTerm?.name ?? ''}：${roomCount} 间教室、${usage.length} 节课占用`
        : '按排课结果看教室占用与空闲'
    "
  >
    <div class="filters">
      <FieldRow label="星期" for-id="wd">
        <select id="wd" v-model.number="weekday">
          <option v-for="d in WEEKDAYS" :key="d.value" :value="d.value">{{ d.text }}</option>
        </select>
      </FieldRow>
      <FieldRow label="起始节" for-id="ss">
        <select id="ss" v-model.number="startSection">
          <option v-for="s in SECTIONS" :key="s" :value="s">{{ s }}</option>
        </select>
      </FieldRow>
      <FieldRow label="结束节" for-id="es">
        <select id="es" v-model.number="endSection">
          <option v-for="s in SECTIONS" :key="s" :value="s">{{ s }}</option>
        </select>
      </FieldRow>
    </div>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="本学期还没有排课"
      empty-detail="教务排好课后，这里会显示教室的占用情况。"
      :skeleton-rows="5"
      @retry="load"
    >
      <section v-if="slot" class="slot">
        <p class="slot__title">
          {{ slot.weekdayText }} 第 {{ slot.startSection }}-{{ slot.endSection }} 节
        </p>
        <p class="slot__line">
          <span class="tag tag--busy">占用中 {{ slot.busy.length }} 间</span>
          <span v-for="b in slot.busy" :key="b.classroom + b.teachingClassCode" class="slot__busy">
            {{ b.classroom }}（{{ b.courseName }}）
          </span>
        </p>
        <p class="slot__line">
          <span class="tag tag--free">空闲 {{ slot.freeRooms.length }} 间</span>
          <span v-for="r in slot.freeRooms" :key="r" class="slot__free">{{ r }}</span>
          <span v-if="!slot.freeRooms.length" class="dim">这个时段没有空教室</span>
        </p>
      </section>

      <DataTable :columns="columns" state="ready" min-width="900px">
        <tr v-for="u in usage" :key="u.classroom + u.teachingClassCode">
          <td>{{ u.classroom }}</td>
          <td class="num">
            {{ u.weekdayText }} {{ u.sectionText }}
            {{ u.startWeek }}-{{ u.endWeek }}周{{ u.weekType === 'ODD' ? '(单)' : u.weekType === 'EVEN' ? '(双)' : '' }}
          </td>
          <td>{{ u.courseName }} <span class="dim">（{{ u.courseCode }}）</span></td>
          <td class="num">{{ u.teachingClassCode }}</td>
          <td>{{ u.teacherName ?? '待定' }}</td>
        </tr>
      </DataTable>
    </StateHost>
  </Plate>
</template>

<style scoped>
.filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--s-3);
  padding: var(--s-4);
  border-bottom: 1px solid var(--line);
  max-width: 620px;
}
.slot {
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
  background: var(--face);
}
.slot__title {
  font-weight: 600;
  font-size: var(--t-sm);
  margin-bottom: var(--s-2);
}
.slot__line {
  font-size: var(--t-sm);
  margin-bottom: var(--s-1);
  line-height: 1.8;
}
.tag {
  display: inline-block;
  margin-right: var(--s-2);
  padding: 0 6px;
  font-size: var(--t-xs);
  color: var(--face);
}
.tag--busy {
  background: var(--accent);
}
.tag--free {
  background: var(--ok);
}
.slot__busy,
.slot__free {
  margin-right: var(--s-3);
  color: var(--ink-muted);
}
.dim {
  color: var(--ink-muted);
  font-size: var(--t-xs);
}
</style>
