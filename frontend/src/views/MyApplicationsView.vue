<script setup lang="ts">
/**
 * 我的申请。
 *
 * 这一页紧跟在智能问答后面：学生问明白规则之后，总得真的去办。
 * 所以表单里的对象是"系统已经替你判过的可选集合"——重修只能挑真正没通过的课，
 * 转专业只能挑学院里的专业；能不能办由服务端预检给结论，界面上如实显示，
 * 而不是等交上去被驳回才知道。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ApiError } from '@/api/client'
import { applicationApi, classroomApi } from '@/api'
import type { ApplicationOption, ApplicationRow, ClassroomSlot } from '@/api/types'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const types = ref<{ code: string; text: string }[]>([])
const options = ref<ApplicationOption[]>([])
const rows = ref<ApplicationRow[]>([])
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const submitting = ref(false)
const lastPrecheck = ref('')

const form = ref({ type: '', targetId: null as number | null, target: '', reason: '', materials: '' })

// 教室借用要判"这个时段这间教室是否被占"，所以单独收结构化时段
const room = ref({ name: '', weekday: 1, startSection: 1, endSection: 2, weeks: '1-16周' })
const slot = ref<ClassroomSlot | null>(null)
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
const isClassroom = computed(() => form.value.type === 'CLASSROOM')

/** 对象是文本的事项（证明名称、替换方式、银行账号、教室时段…），与后端的口径一致 */
const TEXT_TARGET_TYPES = [
  'CERTIFICATE',
  'ENGLISH_SUB',
  'INNOVATION_CREDIT',
  'VETERAN_EXEMPT',
  'BANK_ACCOUNT',
  'MAJOR_DIRECTION',
  'CLASSROOM',
]
const needsTarget = computed(() => !TEXT_TARGET_TYPES.includes(form.value.type))
const needOptions = computed(() => needsTarget.value)
const typeText = computed(() => types.value.find((t) => t.code === form.value.type)?.text ?? '')

const columns: Column[] = [
  { key: 'id', label: '编号', width: '60px', align: 'right' },
  { key: 'type', label: '事项', width: '140px' },
  { key: 'target', label: '对象' },
  { key: 'reason', label: '申请理由' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'review', label: '教务处意见' },
  { key: 'act', label: '操作', width: '90px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    const [list, ts] = await Promise.all([applicationApi.mine(), applicationApi.types()])
    rows.value = list
    types.value = ts.map((s) => {
      const [code, text] = s.split('|')
      return { code, text }
    })
    if (!form.value.type && types.value.length) form.value.type = types.value[0].code
    state.value = list.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '申请记录加载失败'
  }
}

onMounted(async () => {
  await load()
  await loadOptions()
})

async function loadOptions() {
  options.value = []
  form.value.targetId = null
  if (!needOptions.value) return
  try {
    options.value = await applicationApi.options(form.value.type)
    // 后端没给候选时退回文本输入，不要留一个空下拉让人没法提交
    if (!options.value.length) {
      options.value = []
      form.value.targetId = null
    }
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '可选项加载失败', 'bad')
  }
}

watch(() => form.value.type, loadOptions)

/** 选好时段就查出这个时段的占用与空闲，借用时直接挑空教室。 */
async function loadSlot() {
  if (!isClassroom.value) return
  try {
    slot.value = await classroomApi.slot({
      weekday: room.value.weekday,
      startSection: room.value.startSection,
      endSection: room.value.endSection,
    })
    if (!slot.value.freeRooms.includes(room.value.name)) {
      room.value.name = slot.value.freeRooms[0] ?? ''
    }
  } catch {
    slot.value = null
  }
}
watch(
  () => [room.value.weekday, room.value.startSection, room.value.endSection],
  loadSlot,
)
watch(isClassroom, loadSlot)

async function submit() {
  if (form.value.reason.trim().length < 5) {
    toast('申请理由至少写 5 个字，教务处要据此判断', 'bad')
    return
  }
  submitting.value = true
  lastPrecheck.value = ''
  try {
    const chosen = options.value.find((o) => o.id === form.value.targetId)
    const res = await applicationApi.submit({
      type: form.value.type,
      targetId: needsTarget.value ? form.value.targetId : null,
      target: needsTarget.value
        ? (chosen?.label ?? '')
        : isClassroom.value
          ? `${room.value.name} ${WEEKDAYS.find((d) => d.value === room.value.weekday)?.text} 第${room.value.startSection}-${room.value.endSection}节 ${room.value.weeks}`
          : form.value.target,
      reason: form.value.reason,
      materials: form.value.materials,
      roomName: isClassroom.value ? room.value.name : null,
      roomWeekday: isClassroom.value ? room.value.weekday : null,
      roomStartSection: isClassroom.value ? room.value.startSection : null,
      roomEndSection: isClassroom.value ? room.value.endSection : null,
      roomWeeks: isClassroom.value ? room.value.weeks : null,
    })
    lastPrecheck.value = res.precheckNote ?? ''
    toast(`${res.message}${res.precheckNote ? '；' + res.precheckNote : ''}`, 'ok', 8000)
    form.value.reason = ''
    form.value.materials = ''
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '提交失败', 'bad', 8000)
  } finally {
    submitting.value = false
  }
}

async function withdraw(row: ApplicationRow) {
  if (!confirm(`撤回「${row.typeText}」这张申请？`)) return
  try {
    await applicationApi.withdraw(row.id)
    toast('已撤回', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '撤回失败', 'bad')
  }
}
</script>

<template>
  <Plate
    title="我的申请"
    note="免听间听、重新修读、转专业、证明打印都在这里提，提交后由教务处审批"
  >
    <div class="form">
      <FieldRow label="申请事项" for-id="type">
        <select id="type" v-model="form.type">
          <option v-for="t in types" :key="t.code" :value="t.code">{{ t.text }}</option>
        </select>
      </FieldRow>

      <FieldRow
        v-if="needsTarget && options.length"
        label="申请对象"
        for-id="target"
        hint="只列出系统判定可以申请的对象"
      >
        <select id="target" v-model="form.targetId">
          <option :value="null">请选择</option>
          <option v-for="o in options" :key="o.id" :value="o.id">{{ o.label }}</option>
        </select>
      </FieldRow>
      <FieldRow v-else label="申请对象" for-id="cert" hint="写明事项，例如「在读证明」「用雅思 6.0 替换大学英语（四）」">
          <input id="cert" v-model="form.target" placeholder="写明要办理的事项" />
        </FieldRow>

      <FieldRow label="申请理由" for-id="reason" hint="写清为什么办、办了要解决什么问题">
        <textarea id="reason" v-model="form.reason" rows="3" maxlength="500" />
      </FieldRow>

      <template v-if="isClassroom">
        <FieldRow label="星期" for-id="rwd">
          <select id="rwd" v-model.number="room.weekday">
            <option v-for="d in WEEKDAYS" :key="d.value" :value="d.value">{{ d.text }}</option>
          </select>
        </FieldRow>
        <FieldRow label="起始节 / 结束节" for-id="rsec">
          <select id="rsec" v-model.number="room.startSection" class="inline">
            <option v-for="s in SECTIONS" :key="s" :value="s">{{ s }}</option>
          </select>
          <select v-model.number="room.endSection" class="inline">
            <option v-for="s in SECTIONS" :key="s" :value="s">{{ s }}</option>
          </select>
        </FieldRow>
        <FieldRow label="教室" for-id="rroom" :hint="slot ? `该时段空闲 ${slot.freeRooms.length} 间，已占用的不出现在列表里` : '先选时段'">
          <select id="rroom" v-model="room.name">
            <option v-for="r in slot?.freeRooms ?? []" :key="r" :value="r">{{ r }}</option>
          </select>
        </FieldRow>
        <FieldRow label="周次" for-id="rweeks">
          <input id="rweeks" v-model="room.weeks" placeholder="1-16周 / 单周" />
        </FieldRow>
      </template>

      <FieldRow
        label="材料说明"
        for-id="materials"
        hint="手册要求交什么就写什么；创新创业学分认定与退伍免修必须写"
      >
        <input id="materials" v-model="form.materials" maxlength="500" />
      </FieldRow>

      <div class="form__act">
        <Btn variant="solid" :loading="submitting" @click="submit">提交申请</Btn>
        <span v-if="typeText" class="form__hint">正在办理：{{ typeText }}</span>
      </div>
    </div>

    <p v-if="lastPrecheck" class="precheck">
      <span class="precheck__tag">系统预检</span>{{ lastPrecheck }}
    </p>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="还没有提交过申请"
      empty-detail="选好事项、写清理由，提交后教务处会在审批台看到这张单子。"
      :skeleton-rows="4"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="1080px">
        <tr v-for="r in rows" :key="r.id">
          <td class="num num-end">{{ r.id }}</td>
          <td>{{ r.typeText }}</td>
          <td>{{ r.target }}</td>
          <td class="dim">{{ r.reason }}</td>
          <td><StatusPlate :value="r.status" /></td>
          <td class="dim">
            <template v-if="r.reviewer">
              {{ r.reviewer }}：{{ r.reviewNote || '（未填意见）' }}
            </template>
            <template v-else>{{ r.precheckNote ?? '等待教务处审批' }}</template>
          </td>
          <td class="rowact">
            <Btn v-if="r.status === '待审'" variant="quiet" @click="withdraw(r)">撤回</Btn>
            <span v-else class="dim">—</span>
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
  display: flex;
  flex-direction: column;
  gap: var(--s-3);
  max-width: 720px;
}
.form__act {
  display: flex;
  align-items: center;
  gap: var(--s-4);
}
.form__hint {
  font-size: var(--t-sm);
  color: var(--ink-muted);
}
.form__free {
  margin: 0;
}
.precheck {
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
  background: var(--face);
  font-size: var(--t-sm);
  color: var(--ink-muted);
}
.precheck__tag {
  display: inline-block;
  margin-right: var(--s-2);
  padding: 0 6px;
  background: var(--structure);
  color: var(--face);
  font-size: var(--t-xs);
}
</style>
