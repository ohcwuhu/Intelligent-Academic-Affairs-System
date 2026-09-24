<script setup lang="ts">
/**
 * 知识库治理。
 *
 * 这一页把发布门禁做成可见的：门禁不通过时列出具体缺什么，
 * 强行发布必须填理由，理由进审计。切片质量校验也在这里，
 * 检查项是机械可判的，语义完整性仍需人工抽检。
 */
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { knowledgeApi } from '@/api'
import type { KnowledgeDocumentRow } from '@/api/types'
import { toast } from '@/components/useToast'
import { confirmDialog, promptDialog } from '@/components/useDialog'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const rows = ref<KnowledgeDocumentRow[]>([])
const stats = ref<{ documents: number; chunks: number } | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const gateIssues = ref<{ id: number; list: string[] } | null>(null)
const expiring = ref<KnowledgeDocumentRow[]>([])
const quality = ref<{
  id: number
  total: number
  pass: number
  issueCount: number
  issues: { chunkId: number; hierarchyPath: string; issue: string }[]
} | null>(null)

const columns: Column[] = [
  { key: 'id', label: '编号', width: '60px', align: 'right' },
  { key: 'title', label: '文档标题' },
  { key: 'dept', label: '责任部门', width: '100px' },
  { key: 'dates', label: '生效 / 失效', width: '200px' },
  { key: 'auditor', label: '审核人', width: '110px' },
  { key: 'chunks', label: '切片', width: '70px', align: 'right' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'act', label: '操作', width: '260px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    const [docs, s, exp] = await Promise.all([
      knowledgeApi.documents(),
      knowledgeApi.stats(),
      knowledgeApi.expiring(90).catch(() => [] as KnowledgeDocumentRow[]),
    ])
    rows.value = docs
    stats.value = s
    expiring.value = exp
    state.value = docs.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '知识库加载失败'
  }
}
onMounted(load)

async function checkGate(doc: KnowledgeDocumentRow) {
  try {
    gateIssues.value = { id: doc.id, list: await knowledgeApi.gate(doc.id) }
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '门禁检查失败', 'bad')
  }
}

async function checkQuality(doc: KnowledgeDocumentRow) {
  try {
    const r = await knowledgeApi.chunkQuality(doc.id)
    quality.value = { id: doc.id, ...r }
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '切片校验失败', 'bad')
  }
}

async function publish(doc: KnowledgeDocumentRow) {
  const issues = await knowledgeApi.gate(doc.id).catch(() => [])
  let force = false
  let reason = ''
  if (issues.length) {
    const input = await promptDialog({
      title: '发布门禁未通过，要强行发布吗',
      body: `未通过项：\n${issues.map((i) => '· ' + i).join('\n')}\n\n强行发布的理由会记入审计。`,
      label: '强行发布的理由',
      multiline: true,
      required: true,
      requiredHint: '强行发布必须写理由，否则请先补齐材料',
      confirmText: '强行发布',
      danger: true,
    })
    if (input === null) return
    reason = input
    force = true
  }
  try {
    await knowledgeApi.publish(doc.id, force, reason)
    toast(force ? '已强行发布，理由已记入审计' : '已发布', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '发布失败', 'bad')
  }
}

async function expire(doc: KnowledgeDocumentRow) {
  const reason = await promptDialog({
    title: `把「${doc.title}」标记为失效？`,
    body: '失效后检索不再召回这份文档，学生问相关问题会答"知识库没有覆盖"。',
    label: '失效理由',
    multiline: true,
    required: true,
    requiredHint: '失效必须写理由',
    confirmText: '标记失效',
    danger: true,
  })
  if (reason === null) return
  try {
    await knowledgeApi.expire(doc.id, reason)
    toast('已标记失效，检索不再召回', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '操作失败', 'bad')
  }
}

async function reingest() {
  const ok = await confirmDialog({
    title: '重建知识库索引？',
    body: '会重新解析语料并覆盖现有切片。重建后按上一版状态重新生效，切片数应保持 179 片。',
    confirmText: '重建',
    danger: true,
  })
  if (!ok) return
  try {
    const n = await knowledgeApi.reingest()
    toast(`重建完成，共 ${n} 片切片`, 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '重建失败', 'bad')
  }
}
</script>

<template>
  <Plate
    title="知识库治理"
    :note="stats ? `生效文档 ${stats.documents} 份，切片 ${stats.chunks} 片` : ''"
  >
    <template #actions>
      <Btn @click="reingest">重建索引</Btn>
    </template>

    <div v-if="gateIssues" class="panel">
      <p class="panel__title">发布门禁检查结果（文档 {{ gateIssues.id }}）</p>
      <p v-if="!gateIssues.list.length" class="panel__ok">全部通过，可以发布。</p>
      <ul v-else class="panel__list">
        <li v-for="(i, idx) in gateIssues.list" :key="idx">{{ i }}</li>
      </ul>
      <Btn variant="quiet" @click="gateIssues = null">收起</Btn>
    </div>

    <div v-if="quality" class="panel">
      <p class="panel__title">
        切片质量校验（文档 {{ quality.id }}）：共 {{ quality.total }} 片，
        通过 {{ quality.pass }} 片，问题 {{ quality.issueCount }} 处
      </p>
      <ul v-if="quality.issues.length" class="panel__list">
        <li v-for="(i, idx) in quality.issues.slice(0, 20)" :key="idx">
          #{{ i.chunkId }} {{ i.hierarchyPath }}：{{ i.issue }}
        </li>
      </ul>
      <p v-else class="panel__ok">未发现机械可判的问题。语义完整性仍需人工抽检。</p>
      <Btn variant="quiet" @click="quality = null">收起</Btn>
    </div>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="知识库里还没有文档"
      empty-detail="把语料放到配置的路径，然后点重建索引。"
      :skeleton-rows="4"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="1120px">
        <tr v-for="d in rows" :key="d.id">
          <td class="num num-end">{{ d.id }}</td>
          <td>{{ d.title }}</td>
          <td>{{ d.dept }}</td>
          <td class="num">
            {{ d.effectiveDate ?? '未登记' }} / {{ d.expireDate ?? '长期' }}
          </td>
          <td>{{ d.auditor ?? '未登记' }}</td>
          <td class="num num-end">{{ d.chunkCount }}</td>
          <td><StatusPlate :value="d.status" /></td>
          <td class="rowact">
            <Btn variant="quiet" @click="checkGate(d)">门禁</Btn>
            <Btn variant="quiet" @click="checkQuality(d)">切片校验</Btn>
            <Btn v-if="d.status !== '生效'" variant="quiet" @click="publish(d)">发布</Btn>
            <Btn v-else variant="quiet" @click="expire(d)">失效</Btn>
          </td>
        </tr>
      </DataTable>
    </StateHost>
  </Plate>

  <Plate
    title="到期提醒"
    note="未来 90 天内失效的文档。到期前要续期或出替代版本，否则检索会静默少掉一块依据"
  >
    <ul v-if="expiring.length" class="due">
      <li v-for="d in expiring" :key="d.id" class="due__row">
        <span class="due__title">{{ d.title }}</span>
        <span class="due__meta">
          {{ d.dept }} · {{ d.effectiveDate ?? '未登记' }} 起
          <template v-if="d.expireDate"> · {{ d.expireDate }} 失效</template>
          <template v-else> · 未设失效日期</template>
        </span>
        <StatusPlate :value="d.status" />
      </li>
    </ul>
    <p v-else class="due__none">
      近期没有到期的文档。当前语料未设失效日期的按长期有效处理。
    </p>
  </Plate>
</template>

<style scoped>
.due {
  list-style: none;
}
.due__row {
  display: flex;
  align-items: baseline;
  gap: var(--s-4);
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
}
.due__row:last-child {
  border-bottom: 0;
}
.due__title {
  flex: 1;
}
.due__meta {
  font-size: var(--t-sm);
  color: var(--ink-muted);
}
.due__none {
  padding: var(--s-4);
  color: var(--ink-muted);
  font-size: var(--t-sm);
}
.panel {
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
  background: var(--face);
  font-size: var(--t-sm);
}
.panel__title {
  font-weight: 600;
}
.panel__ok {
  color: var(--ok);
  margin-top: var(--s-2);
}
.panel__list {
  margin: var(--s-2) 0;
  padding-left: 1.2em;
  color: var(--ink-muted);
  font-size: var(--t-xs);
}
</style>
