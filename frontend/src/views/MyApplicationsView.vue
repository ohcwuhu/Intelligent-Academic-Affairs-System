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
import { applicationApi } from '@/api'
import type { ApplicationOption, ApplicationRow } from '@/api/types'
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

const needsTarget = computed(() => form.value.type !== 'CERTIFICATE')
const needOptions = computed(() => ['ON_EXEMPT', 'RETAKE', 'TRANSFER_MAJOR'].includes(form.value.type))
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
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '可选项加载失败', 'bad')
  }
}

watch(() => form.value.type, loadOptions)

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
      target: needsTarget.value ? (chosen?.label ?? '') : form.value.target,
      reason: form.value.reason,
      materials: form.value.materials,
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
        v-if="needsTarget"
        label="申请对象"
        for-id="target"
        :hint="needOptions ? '只列出系统判定可以申请的对象' : '写明要办理的事项'"
      >
        <select v-if="needOptions" id="target" v-model="form.targetId">
          <option :value="null">请选择</option>
          <option v-for="o in options" :key="o.id" :value="o.id">{{ o.label }}</option>
        </select>
        <input v-else id="target" v-model="form.target" placeholder="例如 在读证明" />
      </FieldRow>
      <p v-else class="form__free">
        <FieldRow label="证明名称" for-id="cert">
          <input id="cert" v-model="form.target" placeholder="例如 在读证明、成绩证明" />
        </FieldRow>
      </p>

      <FieldRow label="申请理由" for-id="reason" hint="写清为什么办、办了要解决什么问题">
        <textarea id="reason" v-model="form.reason" rows="3" maxlength="500" />
      </FieldRow>

      <FieldRow label="材料说明" for-id="materials" hint="手册要求交什么就写什么，没有就留空">
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
