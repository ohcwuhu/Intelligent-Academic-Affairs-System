<script setup lang="ts">
/**
 * 教学班名单与成绩录入。
 *
 * 这是教师的高频任务：整班录分，并确认没有漏人。所以页面上固定显示
 * 共几人、已录几人、未录几人；未录入行的输入框单独标出来；
 * 保存前先把不合法的分数就地标出来，不让整批因为一条错数据而失败。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { gradeApi, gradeComponentApi, teachingClassApi } from '@/api'
import { downloadCsv } from '@/api/client'
import type { GradeComponent, RosterItem, StudentComponents, TeachingClassVO } from '@/api/types'
import { creditText, gpaText } from '@/utils/format'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'

const route = useRoute()
const classId = Number(route.params.id)

const info = ref<TeachingClassVO | null>(null)
const roster = ref<RosterItem[]>([])
const drafts = reactive<Record<number, string>>({})
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const saving = ref(false)
// 分项成绩：平时/期中/期末。总评仍是上面那个分数，这里解释它怎么来的。
const COMPONENT_ITEMS = ['平时', '期中', '期末']
const components = ref<Map<number, GradeComponent[]>>(new Map())
const editing = ref<RosterItem | null>(null)
const draftComponents = ref<GradeComponent[]>([])
const savingComponents = ref(false)

async function load() {
  state.value = 'loading'
  try {
    const [c, r] = await Promise.all([
      teachingClassApi.get(classId),
      teachingClassApi.roster(classId),
    ])
    info.value = c
    roster.value = r
    for (const k of Object.keys(drafts)) delete drafts[Number(k)]
    for (const item of r) {
      // 用宽松判断：服务端若省略空字段，这里拿到的是 undefined 而不是 null
      drafts[item.enrollmentId] = item.score == null ? '' : String(item.score)
    }
    state.value = r.length ? 'ready' : 'empty'
    const byClass = await gradeComponentApi.byClass(classId).catch(() => [] as StudentComponents[])
    components.value = new Map(byClass.map((s) => [s.enrollmentId, s.items]))
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '名单加载失败'
  }
}

function openComponents(row: RosterItem) {
  editing.value = row
  const existing = components.value.get(row.enrollmentId) ?? []
  draftComponents.value = COMPONENT_ITEMS.map((item) => {
    const hit = existing.find((c) => c.item === item)
    return {
      id: hit?.id ?? null,
      enrollmentId: row.enrollmentId,
      item,
      weight: hit?.weight ?? null,
      score: hit?.score ?? null,
    }
  })
}

async function saveComponents() {
  savingComponents.value = true
  try {
    const items = draftComponents.value.filter((c) => c.score != null || c.weight != null)
    const n = await gradeComponentApi.save(items)
    toast(`已保存 ${n} 项分项成绩`, 'ok')
    editing.value = null
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '保存失败', 'bad')
  } finally {
    savingComponents.value = false
  }
}

/** 权重合计：不强制 100（各课口径不同），只显示出来提醒 */
const weightSum = computed(() =>
  draftComponents.value.reduce((s, c) => s + (c.weight ?? 0), 0),
)

onMounted(load)

/** 校验规则与服务端一致：0 到 100，留空表示撤销录入。 */
function invalid(id: number): string {
  const raw = drafts[id]?.trim() ?? ''
  if (raw === '') return ''
  const n = Number(raw)
  if (Number.isNaN(n)) return '不是数字'
  if (n < 0 || n > 100) return '应在 0 到 100 之间'
  return ''
}

const invalidIds = computed(() =>
  roster.value.filter((r) => invalid(r.enrollmentId)).map((r) => r.enrollmentId),
)
const changedIds = computed(() =>
  roster.value
    .filter((r) => {
      const raw = drafts[r.enrollmentId]?.trim() ?? ''
      const before = r.score == null ? '' : String(r.score)
      return raw !== before
    })
    .map((r) => r.enrollmentId),
)
const counts = computed(() => {
  const entered = roster.value.filter((r) => r.score != null).length
  return { total: roster.value.length, entered, missing: roster.value.length - entered }
})

async function save() {
  if (invalidIds.value.length) {
    toast(`有 ${invalidIds.value.length} 条分数不合法，先修正再保存`, 'bad')
    return
  }
  if (!changedIds.value.length) {
    toast('没有改动需要保存', 'info')
    return
  }
  saving.value = true
  try {
    const entries = changedIds.value.map((id) => {
      const raw = drafts[id]?.trim() ?? ''
      return {
        enrollmentId: id,
        score: raw === '' ? null : Number(raw),
        scoreStatus: raw === '' ? '未录入' : '已录入',
      }
    })
    const n = await gradeApi.batch(entries)
    toast(`已保存 ${n} 条成绩`, 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '保存失败', 'bad')
  } finally {
    saving.value = false
  }
}

const columns: Column[] = [
  { key: 'no', label: '学号', width: '120px' },
  { key: 'name', label: '姓名', width: '110px' },
  { key: 'clazz', label: '班级' },
  { key: 'major', label: '专业' },
  { key: 'component', label: '分项', width: '110px' },
  { key: 'score', label: '成绩', width: '110px', align: 'right' },
  { key: 'gp', label: '绩点', width: '80px', align: 'right' },
  { key: 'pass', label: '是否通过', width: '90px' },
]
</script>

<template>
  <Plate
    :title="info ? `${info.courseName} 名单` : '教学班名单'"
    :note="
      info
        ? `${info.code}　${info.timeText}　${info.classroom || '地点待定'}　${creditText(info.credit)} 学分`
        : ''
    "
  >
    <template #actions>
      <Btn
        variant="quiet"
        @click="() => downloadCsv(`/export/roster`, { id: classId }).then(() => toast('名单已导出', 'ok')).catch((e) => toast(e.message ?? '导出失败', 'bad'))"
      >
        导出名单
      </Btn>
      <Btn variant="solid" :loading="saving" :disabled="!changedIds.length" @click="save">
        保存成绩
      </Btn>
    </template>

    <div class="tally" role="status">
      <p>
        共 <span class="num">{{ counts.total }}</span> 人，
        已录入 <span class="num">{{ counts.entered }}</span> 人，
        未录入 <span class="num" :class="{ 'is-warn': counts.missing > 0 }">{{ counts.missing }}</span> 人
      </p>
      <p v-if="changedIds.length" class="tally__dirty">
        有 <span class="num">{{ changedIds.length }}</span> 条改动尚未保存
      </p>
      <p v-if="invalidIds.length" class="tally__bad">
        有 <span class="num">{{ invalidIds.length }}</span> 条分数不合法
      </p>
    </div>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="这个教学班还没有学生"
      empty-detail="选课结束后名单会自动同步到这里。"
      :skeleton-rows="6"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="820px">
        <tr v-for="r in roster" :key="r.enrollmentId">
          <td><span class="num">{{ r.studentNo }}</span></td>
          <td>{{ r.studentName }}</td>
          <td>{{ r.clazzName }}</td>
          <td>{{ r.majorName }}</td>
          <td>
            <Btn variant="quiet" @click="openComponents(r)">
              {{ components.get(r.enrollmentId)?.length ? '分项成绩' : '录分项' }}
            </Btn>
          </td>
          <td class="num num-end">
            <input
              v-model="drafts[r.enrollmentId]"
              class="score-input num"
              inputmode="decimal"
              :data-empty="r.score == null"
              :aria-label="`${r.studentName} 的成绩`"
              :aria-invalid="!!invalid(r.enrollmentId)"
            />
            <span v-if="invalid(r.enrollmentId)" class="score-err">{{ invalid(r.enrollmentId) }}</span>
          </td>
          <td class="num num-end">{{ r.gradePoint === null ? '-' : gpaText(r.gradePoint) }}</td>
          <td>
            <span v-if="r.score == null" class="mark mark--none">未录入</span>
            <span v-else-if="r.passed" class="mark mark--ok">通过</span>
            <span v-else class="mark mark--bad">未通过</span>
          </td>
        </tr>
      </DataTable>
    </StateHost>
  </Plate>

  <div v-if="editing" class="drawer" role="dialog" aria-modal="true">
    <div class="drawer__panel">
      <header class="drawer__head">
        <p class="drawer__title">
          {{ editing.studentName }}（{{ editing.studentNo }}）的分项成绩
        </p>
        <Btn variant="quiet" @click="editing = null">关闭</Btn>
      </header>
      <p class="drawer__hint">
        总评成绩仍是名单里那一个数；这里解释它是怎么来的。
        权重合计当前为 <span class="num">{{ weightSum }}</span>%（不同课程口径不同，不强制等于 100）。
      </p>
      <table class="items">
        <thead>
          <tr><th>项目</th><th>权重 %</th><th>分数</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in draftComponents" :key="c.item">
            <td>{{ c.item }}</td>
            <td><input v-model.number="c.weight" class="num" inputmode="decimal" /></td>
            <td><input v-model.number="c.score" class="num" inputmode="decimal" /></td>
          </tr>
        </tbody>
      </table>
      <div class="drawer__act">
        <Btn variant="solid" :loading="savingComponents" @click="saveComponents">保存分项</Btn>
      </div>
    </div>
  </div>
</template>

<style scoped>
.drawer {
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  background: rgba(19, 22, 25, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--s-6);
}
.drawer__panel {
  width: min(520px, 100%);
  background: var(--face-raised);
  border: 1px solid var(--line-strong);
  padding: var(--s-4);
}
.drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-3);
  margin-bottom: var(--s-3);
}
.drawer__title {
  font-weight: 600;
}
.drawer__hint {
  font-size: var(--t-xs);
  color: var(--ink-muted);
  margin-bottom: var(--s-3);
}
.items {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--t-sm);
}
.items th,
.items td {
  border-bottom: 1px solid var(--line);
  padding: var(--s-1) var(--s-2);
  text-align: left;
}
.items input {
  width: 90px;
  height: 28px;
  padding: 0 var(--s-2);
  border: 1px solid var(--line-strong);
  background: var(--face);
}
.drawer__act {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--s-3);
}
.tally {
  display: flex;
  gap: var(--s-6);
  flex-wrap: wrap;
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
  font-size: var(--t-sm);
}

.tally__dirty {
  color: var(--accent-deep);
}

.tally__bad {
  color: var(--bad);
}

.is-warn {
  color: var(--accent-deep);
  font-weight: 600;
}

.score-input {
  width: 64px;
  height: 26px;
  padding: 0 var(--s-2);
  text-align: right;
  background: var(--face-raised);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  font-size: var(--t-sm);
}

.score-input:focus {
  border-color: var(--accent-deep);
}

/* 未录入的行把输入框本身标出来，而不是给整行加一条侧边色条 */
.score-input[data-empty='true'] {
  border-color: var(--accent);
}

.score-input[aria-invalid='true'] {
  border-color: var(--bad);
}

.score-err {
  display: block;
  font-size: var(--t-2xs);
  color: var(--bad);
  text-align: right;
}

.mark {
  font-size: var(--t-xs);
  font-weight: 600;
}

.mark--ok {
  color: var(--ok);
}

.mark--bad {
  color: var(--bad);
}

.mark--none {
  color: var(--ink-muted);
  font-weight: 400;
}
</style>
