<script setup lang="ts">
/**
 * 教学班开课维护。
 * 保存后服务端返回排课冲突（教师撞课、教室被占用），这里原样呈现，不阻断保存。
 */
import { onMounted, ref } from 'vue'
import { ApiError, downloadCsv } from '@/api/client'
import { basicApi, courseApi, teachingClassApi, teacherApi } from '@/api'
import type { Course, ScheduleConflict, Teacher, TeachingClassVO, Term } from '@/api/types'
import { creditText } from '@/utils/format'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const rows = ref<TeachingClassVO[]>([])
const courses = ref<Course[]>([])
const teachers = ref<Teacher[]>([])
const terms = ref<Term[]>([])
const conflicts = ref<ScheduleConflict[]>([])
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const editing = ref<Record<string, unknown> | null>(null)
const formError = ref('')
const termFilter = ref('')

const columns: Column[] = [
  { key: 'code', label: '教学班代码', width: '190px' },
  { key: 'course', label: '课程' },
  { key: 'teacher', label: '任课教师', width: '100px' },
  { key: 'time', label: '上课时间', width: '200px' },
  { key: 'room', label: '地点', width: '130px' },
  { key: 'seat', label: '选课 / 容量', width: '110px', align: 'right' },
  { key: 'status', label: '状态', width: '80px' },
  { key: 'act', label: '操作', width: '130px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    const [list, term] = await Promise.all([
      teachingClassApi.list({ termId: termFilter.value ? Number(termFilter.value) : undefined }),
      basicApi.terms(),
    ])
    rows.value = list
    terms.value = term
    state.value = list.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '教学班加载失败'
  }
}

onMounted(async () => {
  const [c, t] = await Promise.all([
    courseApi.page({ page: 1, size: 100 }).catch(() => ({ records: [] as Course[] })),
    teacherApi.page({ page: 1, size: 100 }).catch(() => ({ records: [] as Teacher[] })),
  ])
  courses.value = c.records
  teachers.value = t.records
  await load()
})

function startCreate() {
  formError.value = ''
  conflicts.value = []
  editing.value = {
    id: null,
    courseId: courses.value[0]?.id ?? null,
    teacherId: teachers.value[0]?.id ?? null,
    termId: terms.value.find((t) => t.isCurrent === 1)?.id ?? terms.value[0]?.id ?? null,
    capacity: 60,
    weekday: 1,
    startSection: 1,
    endSection: 2,
    startWeek: 1,
    endWeek: 16,
    weekType: 'ALL',
    classroom: '',
    status: '开放',
  }
}

async function save() {
  const f = editing.value
  if (!f) return
  if (!f.courseId || !f.teacherId || !f.termId) {
    formError.value = '课程、任课教师与学期都必须选择'
    return
  }
  if (Number(f.startSection) > Number(f.endSection)) {
    formError.value = '开始节次不能大于结束节次'
    return
  }
  if (Number(f.startWeek) > Number(f.endWeek)) {
    formError.value = '起始周不能大于结束周'
    return
  }
  try {
    const res = await teachingClassApi.save(f)
    conflicts.value = res.conflicts ?? []
    if (conflicts.value.length) {
      toast(`已保存，但有 ${conflicts.value.length} 处排课冲突需要确认`, 'bad', 6000)
    } else {
      toast('已保存，未发现排课冲突', 'ok')
    }
    editing.value = null
    await load()
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : '保存失败'
  }
}

async function remove(tc: TeachingClassVO) {
  if (!confirm(`确认删除教学班 ${tc.code}？`)) return
  try {
    await teachingClassApi.remove(tc.id)
    toast('已删除', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '删除失败', 'bad')
  }
}
</script>

<template>
  <Plate title="教学班开课" :note="`共 ${rows.length} 个教学班`">
    <template #actions>
      <Btn
        variant="quiet"
        @click="() => downloadCsv('/export/teaching-classes', { termId: termFilter ? Number(termFilter) : undefined }).then(() => toast('已导出开课表', 'ok')).catch((e) => toast(e.message ?? '导出失败', 'bad'))"
      >
        导出
      </Btn>
      <Btn variant="solid" @click="startCreate">新增开课</Btn>
    </template>

    <form v-if="editing" class="editor" @submit.prevent="save">
      <p class="editor__title">{{ editing.id ? '修改教学班' : '新增教学班' }}</p>
      <div class="editor__grid">
        <FieldRow label="课程">
          <select v-model.number="editing.courseId">
            <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.code }} {{ c.name }}</option>
          </select>
        </FieldRow>
        <FieldRow label="任课教师">
          <select v-model.number="editing.teacherId">
            <option v-for="t in teachers" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
        </FieldRow>
        <FieldRow label="学期">
          <select v-model.number="editing.termId">
            <option v-for="t in terms" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
        </FieldRow>
        <FieldRow label="容量"><input v-model.number="editing.capacity" type="number" class="num" /></FieldRow>
        <FieldRow label="星期">
          <select v-model.number="editing.weekday">
            <option v-for="d in 7" :key="d" :value="d">周{{ ['一','二','三','四','五','六','日'][d - 1] }}</option>
          </select>
        </FieldRow>
        <FieldRow label="开始节次"><input v-model.number="editing.startSection" type="number" min="1" max="12" class="num" /></FieldRow>
        <FieldRow label="结束节次"><input v-model.number="editing.endSection" type="number" min="1" max="12" class="num" /></FieldRow>
        <FieldRow label="起始周"><input v-model.number="editing.startWeek" type="number" min="1" max="30" class="num" /></FieldRow>
        <FieldRow label="结束周"><input v-model.number="editing.endWeek" type="number" min="1" max="30" class="num" /></FieldRow>
        <FieldRow label="单双周">
          <select v-model="editing.weekType">
            <option value="ALL">每周</option><option value="ODD">单周</option><option value="EVEN">双周</option>
          </select>
        </FieldRow>
        <FieldRow label="上课地点"><input v-model="editing.classroom" /></FieldRow>
        <FieldRow label="状态">
          <select v-model="editing.status">
            <option>开放</option><option>停开</option><option>结课</option>
          </select>
        </FieldRow>
      </div>
      <p v-if="formError" class="editor__err">{{ formError }}</p>
      <div class="editor__act">
        <Btn variant="solid" type="submit">保存</Btn>
        <Btn @click="editing = null">取消</Btn>
      </div>
    </form>

    <div v-if="conflicts.length" class="conflict">
      <p class="conflict__title">上次保存产生的排课冲突</p>
      <ul>
        <li v-for="(c, i) in conflicts" :key="i">
          {{ c.type === 'TEACHER' ? '教师时间冲突' : '教室占用冲突' }}：{{ c.conflictWith }}（{{ c.timeText }}）
        </li>
      </ul>
    </div>

    <form class="filters" @submit.prevent="load">
      <FieldRow label="学期">
        <select v-model="termFilter">
          <option value="">全部学期</option>
          <option v-for="t in terms" :key="t.id" :value="String(t.id)">{{ t.name }}</option>
        </select>
      </FieldRow>
      <Btn variant="solid" type="submit">查询</Btn>
    </form>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="这个学期还没有开课"
      empty-detail="新增一条开课记录后，学生就能在选课页看到它。"
      :skeleton-rows="6"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="1060px">
        <tr v-for="tc in rows" :key="tc.id">
          <td><span class="num">{{ tc.code }}</span></td>
          <td>{{ tc.courseName }}<span class="dim"> · {{ creditText(tc.credit) }} 学分</span></td>
          <td>{{ tc.teacherName }}</td>
          <td>{{ tc.timeText }}</td>
          <td>{{ tc.classroom || '-' }}</td>
          <td class="num num-end">{{ tc.enrolled }} / {{ tc.capacity }}</td>
          <td><StatusPlate :value="tc.status" /></td>
          <td class="rowact">
            <Btn
              variant="quiet"
              @click="
                editing = { id: tc.id, code: tc.code, courseId: tc.courseId, teacherId: tc.teacherId,
                            termId: tc.termId, capacity: tc.capacity, weekday: tc.weekday,
                            startSection: tc.startSection, endSection: tc.endSection,
                            startWeek: tc.startWeek, endWeek: tc.endWeek, weekType: tc.weekType,
                            classroom: tc.classroom, status: tc.status }
              "
            >
              修改
            </Btn>
            <Btn variant="quiet" @click="remove(tc)">删除</Btn>
          </td>
        </tr>
      </DataTable>
    </StateHost>
  </Plate>
</template>

<style scoped>
.conflict {
  border-bottom: 1px solid var(--line);
  border-top: 1px solid var(--accent);
  padding: var(--s-3) var(--s-4);
}
.conflict__title {
  font-size: var(--t-sm);
  font-weight: 600;
  color: var(--accent-deep);
}
.conflict ul {
  margin: var(--s-2) 0 0;
  padding-left: 1.2em;
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
</style>
