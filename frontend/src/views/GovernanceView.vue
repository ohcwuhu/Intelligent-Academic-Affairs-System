<script setup lang="ts">
/**
 * 反馈与知识缺口。
 *
 * 这两块是同一个闭环的两端：用户说这条回答有误 → 进入反馈待办；
 * 系统反复答不上来 → 进入知识缺口待办。处理结果回流到评测集，
 * 否则反馈只是一堆没人看的意见。
 */
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { feedbackApi, governanceApi } from '@/api'
import type { AuditRow, FeedbackRow, GovernanceOverview, KnowledgeGapRow } from '@/api/types'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const overview = ref<GovernanceOverview | null>(null)
const feedback = ref<FeedbackRow[]>([])
const gaps = ref<KnowledgeGapRow[]>([])
const audit = ref<AuditRow[]>([])
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

const fbColumns: Column[] = [
  { key: 'id', label: '编号', width: '60px', align: 'right' },
  { key: 'question', label: '问题' },
  { key: 'type', label: '类型', width: '90px' },
  { key: 'detail', label: '用户说明' },
  { key: 'from', label: '提交人', width: '100px' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'act', label: '操作', width: '150px', align: 'right' },
]
const gapColumns: Column[] = [
  { key: 'q', label: '问题' },
  { key: 'n', label: '出现次数', width: '90px', align: 'right' },
  { key: 'reason', label: '原因', width: '200px' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'act', label: '操作', width: '150px', align: 'right' },
]
const auditColumns: Column[] = [
  { key: 'time', label: '时间', width: '160px' },
  { key: 'type', label: '事件', width: '90px' },
  { key: 'user', label: '用户', width: '100px' },
  { key: 'q', label: '问题' },
  { key: 'intent', label: '意图', width: '80px' },
  { key: 'mode', label: '方式', width: '90px' },
  { key: 'ms', label: '耗时', width: '70px', align: 'right' },
  { key: 'blocked', label: '拦截', width: '60px' },
]

const typeText: Record<string, string> = {
  USEFUL: '有用',
  USELESS: '没用',
  WRONG: '内容有误',
}

async function load() {
  state.value = 'loading'
  try {
    const [o, f, g, a] = await Promise.all([
      governanceApi.overview(),
      feedbackApi.page({ page: 1, size: 20 }),
      governanceApi.gaps({ page: 1, size: 20 }),
      governanceApi.audit({ page: 1, size: 20 }),
    ])
    overview.value = o
    feedback.value = f.records
    gaps.value = g.records
    audit.value = a.records
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '治理后台加载失败'
  }
}
onMounted(load)

async function handleFeedback(row: FeedbackRow) {
  const note = prompt('处理说明（会记录处理人与时间）') ?? ''
  if (!note.trim()) return
  try {
    await feedbackApi.handle(row.id, '已修正', note)
    toast('已标记修正，评测脚本会把它纳入回归用例', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '处理失败', 'bad')
  }
}

async function handleGap(row: KnowledgeGapRow) {
  const note = prompt('处理说明，例如已补录哪份文件的哪一条') ?? ''
  if (!note.trim()) return
  try {
    await governanceApi.handleGap(row.id, '已补录', '教务处', note)
    toast('已记录补录结果', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '处理失败', 'bad')
  }
}
</script>

<template>
  <Plate title="反馈与知识缺口" note="用户的纠错与系统的答不上来，在这里汇总成可跟踪的待办">
    <dl v-if="overview" class="ledger">
      <div><dt>待处理反馈</dt><dd class="num">{{ overview.pendingFeedback }}</dd></div>
      <div><dt>待处理缺口</dt><dd class="num">{{ overview.pendingGap }}</dd></div>
      <div><dt>今日提问</dt><dd class="num">{{ overview.askToday }}</dd></div>
      <div><dt>今日拦截</dt><dd class="num">{{ overview.blockedToday }}</dd></div>
      <div><dt>注入尝试</dt><dd class="num">{{ overview.injectionToday }}</dd></div>
      <div><dt>平均耗时</dt><dd class="num">{{ overview.avgDurationMs }}ms</dd></div>
    </dl>

    <StateHost :state="state" :error-detail="errorDetail" :skeleton-rows="6" @retry="load">
      <section class="block">
        <h3 class="block__title">用户反馈</h3>
        <DataTable :columns="fbColumns" state="ready" min-width="1000px">
          <tr v-for="f in feedback" :key="f.id">
            <td class="num num-end">{{ f.id }}</td>
            <td>{{ f.question }}</td>
            <td>{{ typeText[f.type] ?? f.type }}</td>
            <td class="dim">{{ f.detail ?? '—' }}</td>
            <td>{{ f.username ?? '—' }}</td>
            <td><StatusPlate :value="f.status" /></td>
            <td class="rowact">
              <Btn v-if="f.status === '待处理'" variant="quiet" @click="handleFeedback(f)">
                标记已修正
              </Btn>
              <span v-else class="dim">{{ f.handleNote ?? '' }}</span>
            </td>
          </tr>
        </DataTable>
        <p v-if="!feedback.length" class="none">还没有用户反馈。</p>
      </section>

      <section class="block">
        <h3 class="block__title">知识缺口</h3>
        <DataTable :columns="gapColumns" state="ready" min-width="900px">
          <tr v-for="g in gaps" :key="g.id">
            <td>{{ g.sampleQuestion }}</td>
            <td class="num num-end">{{ g.hitCount }}</td>
            <td class="dim">{{ g.reason ?? '—' }}</td>
            <td><StatusPlate :value="g.status" /></td>
            <td class="rowact">
              <Btn v-if="g.status === '待处理'" variant="quiet" @click="handleGap(g)">
                标记已补录
              </Btn>
              <span v-else class="dim">{{ g.note ?? '' }}</span>
            </td>
          </tr>
        </DataTable>
        <p v-if="!gaps.length" class="none">还没有知识缺口。</p>
      </section>

      <section class="block">
        <h3 class="block__title">审计日志（只读）</h3>
        <DataTable :columns="auditColumns" state="ready" min-width="1080px">
          <tr v-for="a in audit" :key="a.id">
            <td class="num">{{ a.createdAt }}</td>
            <td>{{ a.eventType }}</td>
            <td>{{ a.username ?? '系统' }}</td>
            <td class="dim">{{ a.question ?? a.reason ?? '—' }}</td>
            <td>{{ a.intent ?? '—' }}</td>
            <td>{{ a.mode ?? '—' }}</td>
            <td class="num num-end">{{ a.durationMs ?? '—' }}</td>
            <td>{{ a.blocked === 1 ? '是' : '' }}</td>
          </tr>
        </DataTable>
      </section>
    </StateHost>
  </Plate>
</template>

<style scoped>
.ledger {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin: 0;
}
.ledger > div {
  padding: var(--s-4);
  border-right: 1px solid var(--line);
}
.ledger > div:last-child {
  border-right: 0;
}
.ledger dt {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.ledger dd {
  margin: var(--s-1) 0 0;
  font-size: var(--t-lg);
  font-weight: 600;
}
.block {
  border-top: 1px solid var(--line);
}
.block__title {
  font-size: var(--t-base);
  font-weight: 600;
  padding: var(--s-3) var(--s-4);
}
.none {
  padding: 0 var(--s-4) var(--s-4);
  font-size: var(--t-sm);
  color: var(--ink-muted);
}
@media (max-width: 1100px) {
  .ledger {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 900px) {
  .ledger {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
