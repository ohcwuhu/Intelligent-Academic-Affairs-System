<script setup lang="ts">
/**
 * 考试安排（教务侧）。
 *
 * 与排课一致的做法：保存时把冲突摆出来但不阻断。同一教室同一时段被两场考试
 * 占用在真实教务里是常事（分考场、借教室），系统越俎代庖去禁止，反而逼着
 * 教务绕过系统。所以这里只做"看得见的提醒"。
 */
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '@/api/client'
import { examApi, teachingClassApi } from '@/api'
import type { ExamRow, TeachingClassVO } from '@/api/types'
import { toast } from '@/components/useToast'
import { useCurrentTerm } from '@/components/useTerm'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'

const { currentTerm, load: loadTerm } = useCurrentTerm()
const rows = ref<ExamRow[]>([])
const classes = ref<TeachingClassVO[]>([])
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const saving = ref(false)
const editing = ref(false)
const conflicts = ref<{ kind: string; message: string }[]>([])

const form = reactive({
  id: null as number | null,
  teachingClassId: null as number | null,
  examType: '期末考试',
  examDate: '',
  startTime: '09:00',
  endTime: '11:00',
  classroom: '',
  seatNo: '',
  note: '',
})

const columns: Column[] = [
  { key: 'date', label: '日期', width: '120px' },
  { key: 'time', label: '时间', width: '140px' },
  { key: 'class', label: '教学班' },
  { key: 'course', label: '课程', width: '150px' },
  { key: 'room', label: '考场', width: '130px' },
  { key: 'type', label: '类型', width: '90px' },
  { key: 'act', label: '操作', width: '130px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    await loadTerm()
    const termId = currentTerm.value?.id
    const [exams, list] = await Promise.all([
      examApi.list({ termId }),
      teachingClassApi.list({ termId }),
    ])
    rows.value = exams
    classes.value = list
    state.value = exams.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '考试安排加载失败'
  }
}
onMounted(load)

function startCreate() {
  editing.value = true
  conflicts.value = []
  Object.assign(form, {
    id: null,
    teachingClassId: classes.value[0]?.id ?? null,
    examType: '期末考试',
    examDate: '',
    startTime: '09:00',
    endTime: '11:00',
    classroom: '',
    seatNo: '',
    note: '',
  })
}

function startEdit(row: ExamRow) {
  editing.value = true
  conflicts.value = []
  Object.assign(form, {
    id: row.id,
    teachingClassId: row.teachingClassId,
    examType: row.examType,
    examDate: row.examDate,
    startTime: row.startTime,
    endTime: row.endTime,
    classroom: row.classroom ?? '',
    seatNo: row.seatNo ?? '',
    note: row.note ?? '',
  })
}

async function save() {
  saving.value = true
  conflicts.value = []
  try {
    const res = await examApi.save({ ...form })
    conflicts.value = res.conflicts ?? []
    toast(
      conflicts.value.length
        ? `已保存，但发现 ${conflicts.value.length} 处冲突，请看下方提示`
        : '已保存',
      conflicts.value.length ? 'info' : 'ok',
      7000,
    )
    editing.value = false
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '保存失败', 'bad')
  } finally {
    saving.value = false
  }
}

async function remove(row: ExamRow) {
  if (!confirm(`删除「${row.courseName}」${row.examDate} 的考试安排？`)) return
  try {
    await examApi.remove(row.id)
    toast('已删除', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '删除失败', 'bad')
  }
}
</script>

<template>
  <Plate
    title="考试安排"
    :note="`${currentTerm?.name ?? ''}，共 ${rows.length} 场；保存时只提示冲突，不阻止调整`"
  >
    <template #actions>
      <Btn variant="solid" @click="startCreate">新增考试</Btn>
    </template>

    <div v-if="editing" class="form">
      <FieldRow label="教学班" for-id="tc">
        <select id="tc" v-model="form.teachingClassId">
          <option v-for="c in classes" :key="c.id" :value="c.id">
            {{ c.courseName }}（{{ c.code }}）
          </option>
        </select>
      </FieldRow>
      <FieldRow label="类型" for-id="etype">
        <select id="etype" v-model="form.examType">
          <option>期末考试</option>
          <option>补考</option>
          <option>重修考试</option>
        </select>
      </FieldRow>
      <FieldRow label="日期" for-id="edate" hint="格式 2027-01-05">
        <input id="edate" v-model="form.examDate" placeholder="2027-01-05" />
      </FieldRow>
      <FieldRow label="开始" for-id="estart">
        <input id="estart" v-model="form.startTime" placeholder="09:00" />
      </FieldRow>
      <FieldRow label="结束" for-id="eend">
        <input id="eend" v-model="form.endTime" placeholder="11:00" />
      </FieldRow>
      <FieldRow label="考场" for-id="eroom">
        <input id="eroom" v-model="form.classroom" placeholder="博学楼A201" />
      </FieldRow>
      <FieldRow label="座位/说明" for-id="enote" hint="座位号可留空；说明写闭卷/开卷/上机">
        <input id="enote" v-model="form.note" placeholder="闭卷" />
      </FieldRow>
      <div class="form__act">
        <Btn variant="solid" :loading="saving" @click="save">保存</Btn>
        <Btn variant="quiet" @click="editing = false">取消</Btn>
      </div>
    </div>

    <ul v-if="conflicts.length" class="conflicts">
      <li v-for="(c, i) in conflicts" :key="i">
        <span class="conflicts__kind">{{ c.kind === 'CLASSROOM' ? '教室占用' : '教学班冲突' }}</span>
        {{ c.message }}
      </li>
    </ul>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="本学期还没有安排考试"
      empty-detail="点右上角新增，填日期、时段与考场。"
      :skeleton-rows="5"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="1040px">
        <tr v-for="r in rows" :key="r.id">
          <td class="num">{{ r.examDate }}</td>
          <td class="num">{{ r.startTime }}-{{ r.endTime }}</td>
          <td><span class="num">{{ r.teachingClassCode }}</span></td>
          <td>{{ r.courseName }}</td>
          <td>{{ r.classroom || '待定' }}</td>
          <td>{{ r.examType }}</td>
          <td class="rowact">
            <Btn variant="quiet" @click="startEdit(r)">修改</Btn>
            <Btn variant="quiet" @click="remove(r)">删除</Btn>
          </td>
        </tr>
      </DataTable>
    </StateHost>
  </Plate>
</template>

<style scoped>
.form {
  padding: var(--s-4);
  border-bottom: 1px solid var(--line);
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--s-3);
}

.form__act {
  display: flex;
  align-items: flex-end;
  gap: var(--s-2);
}

.conflicts {
  margin: 0;
  padding: var(--s-3) var(--s-4);
  list-style: none;
  border-bottom: 1px solid var(--line);
  background: var(--face);
  font-size: var(--t-sm);
}

.conflicts__kind {
  display: inline-block;
  margin-right: var(--s-2);
  padding: 0 6px;
  background: var(--accent);
  color: var(--face);
  font-size: var(--t-xs);
}
</style>
