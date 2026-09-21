<script setup lang="ts">
/**
 * 数据导入。
 *
 * 教务的数据不是一条条填的：课程库、学生名册、教师名册都是整理好的表格。
 * 所以这一页只做三件事：给模板、先校验、再入库。
 * 「先校验」不是多余的步骤——一次导入几百行，先看清哪一行不合格，
 * 比导完再回滚要省事得多。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ApiError } from '@/api/client'
import { importApi } from '@/api'
import type { ImportReport, ImportTarget } from '@/api/types'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'

const targets = ref<ImportTarget[]>([])
const type = ref('')
const file = ref<File | null>(null)
const report = ref<ImportReport | null>(null)
const busy = ref(false)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const errorDetail = ref('')
const dictionary = ref<{ colleges: Record<string, string>; majors: Record<string, string>; clazzes: Record<string, string> } | null>(null)

const current = computed(() => targets.value.find((t) => t.type === type.value) ?? null)

const columns: Column[] = [
  { key: 'line', label: '行号', width: '70px', align: 'right' },
  { key: 'key', label: '业务键', width: '140px' },
  { key: 'message', label: '问题' },
]

async function load() {
  state.value = 'loading'
  try {
    const [ts, dict] = await Promise.all([importApi.targets(), importApi.dictionary()])
    targets.value = ts
    dictionary.value = dict
    type.value = ts[0]?.type ?? ''
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '导入配置加载失败'
  }
}
onMounted(load)

watch(type, () => {
  file.value = null
  report.value = null
})

function pick(e: Event) {
  const input = e.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
  report.value = null
}

/** 模板由后端的表头定义生成，避免模板与校验规则各写一份。 */
function downloadTemplate() {
  const t = current.value
  if (!t) return
  const csv = [t.columns.join(','), t.sample.join(',')].join('\n') + '\n'
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${t.label}-导入模板.csv`
  a.click()
  URL.revokeObjectURL(url)
}

async function run(commit: boolean) {
  if (!file.value) {
    toast('先选一个文件', 'bad')
    return
  }
  busy.value = true
  try {
    report.value = commit
      ? await importApi.commit(type.value, file.value)
      : await importApi.preview(type.value, file.value)
    if (report.value.committed) {
      toast(`已入库 ${report.value.ok} 行`, 'ok')
    } else if (report.value.failed) {
      toast(`有 ${report.value.failed} 行不合格，先改文件再导`, 'bad', 7000)
    } else {
      toast(`校验通过，共 ${report.value.ok} 行，可以入库`, 'ok')
    }
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '导入失败', 'bad', 8000)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Plate
    title="数据导入"
    note="课程库、学生名册、教师名册按模板批量导入；先校验，再入库"
  >
    <StateHost :state="state" :error-detail="errorDetail" :skeleton-rows="4" @retry="load">
      <div class="form">
        <FieldRow label="导入类型" for-id="itype">
          <select id="itype" v-model="type">
            <option v-for="t in targets" :key="t.type" :value="t.type">{{ t.label }}</option>
          </select>
        </FieldRow>
        <FieldRow label="文件" for-id="ifile" hint="支持 .xlsx / .xls / .csv；第一行必须是表头">
          <input id="ifile" type="file" accept=".xlsx,.xls,.csv" @change="pick" />
        </FieldRow>
        <div class="form__act">
          <Btn variant="quiet" @click="downloadTemplate">下载模板</Btn>
          <Btn :loading="busy" @click="run(false)">先校验</Btn>
          <Btn
            variant="solid"
            :loading="busy"
            :disabled="!report || report.failed > 0 || report.committed"
            @click="run(true)"
          >
            确认导入
          </Btn>
        </div>
      </div>

      <p v-if="current" class="hint">{{ current.note }}</p>

      <div v-if="dictionary" class="dict">
        <p class="dict__title">系统里已有的代码（填表时对照）</p>
        <dl>
          <div>
            <dt>学院</dt>
            <dd>
              <span v-for="(name, code) in dictionary.colleges" :key="code" class="dict__item">
                <span class="num">{{ code }}</span> {{ name }}
              </span>
            </dd>
          </div>
          <div>
            <dt>专业</dt>
            <dd>
              <span v-for="(name, code) in dictionary.majors" :key="code" class="dict__item">
                <span class="num">{{ code }}</span> {{ name }}
              </span>
            </dd>
          </div>
          <div>
            <dt>班级</dt>
            <dd>
              <span v-for="(name, code) in dictionary.clazzes" :key="code" class="dict__item">
                <span class="num">{{ code }}</span> {{ name }}
              </span>
            </dd>
          </div>
        </dl>
      </div>

      <div v-if="report" class="report" :class="{ 'report--ok': report.committed }">
        <p class="report__head">
          {{ report.fileName }}：共 <span class="num">{{ report.total }}</span> 行，
          合格 <span class="num">{{ report.ok }}</span> 行，
          不合格 <span class="num">{{ report.failed }}</span> 行
          <template v-if="report.committed">，已入库</template>
          <template v-else-if="report.failed">，未入库</template>
        </p>
        <DataTable v-if="report.rows.length" :columns="columns" state="ready" min-width="520px">
          <tr v-for="r in report.rows" :key="r.line">
            <td class="num num-end">{{ r.line }}</td>
            <td class="num">{{ r.key || '—' }}</td>
            <td>{{ r.message }}</td>
          </tr>
        </DataTable>
        <p v-else class="report__clean">所有行都通过了校验。</p>
      </div>
    </StateHost>
  </Plate>
</template>

<style scoped>
.form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--s-3);
  padding: var(--s-4);
  border-bottom: 1px solid var(--line);
}
.form__act {
  display: flex;
  align-items: flex-end;
  gap: var(--s-2);
}
.hint {
  padding: var(--s-3) var(--s-4);
  font-size: var(--t-sm);
  color: var(--ink-muted);
  border-bottom: 1px solid var(--line);
}
.dict {
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
  background: var(--face);
  font-size: var(--t-sm);
}
.dict__title {
  font-weight: 600;
  margin-bottom: var(--s-2);
}
.dict dt {
  color: var(--ink-muted);
  font-size: var(--t-xs);
}
.dict dd {
  margin: 2px 0 var(--s-2);
}
.dict__item {
  display: inline-block;
  margin-right: var(--s-4);
}
.report {
  padding: var(--s-4);
}
.report--ok {
  background: var(--face);
}
.report__head {
  font-size: var(--t-sm);
  margin-bottom: var(--s-2);
}
.report__clean {
  font-size: var(--t-sm);
  color: var(--ok);
}
</style>
