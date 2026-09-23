<script setup lang="ts">
/**
 * 学分收费查询。
 *
 * 收费是最不能含糊的地方：每一笔都要写清"哪门课、多少学分、按哪个项目、单价多少"。
 * 所以这一页把明细摆出来，而不是只给一个合计。
 * 单价由教务维护；手册里没有金额（第二十三条只说按学院规定执行），
 * 所以页面上明确标注当前是演示标准。
 */
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { feeApi } from '@/api'
import { downloadCsv } from '@/api/client'
import type { FeeBill, FeeRule } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useCurrentTerm } from '@/components/useTerm'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'

const auth = useAuthStore()
const { currentTerm, load: loadTerm } = useCurrentTerm()
const rules = ref<FeeRule[]>([])
const bill = ref<FeeBill | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

const billColumns: Column[] = [
  { key: 'course', label: '课程' },
  { key: 'credit', label: '学分', width: '80px', align: 'right' },
  { key: 'item', label: '收费项目', width: '130px' },
  { key: 'price', label: '单价(元/学分)', width: '120px', align: 'right' },
  { key: 'amount', label: '金额(元)', width: '100px', align: 'right' },
  { key: 'reason', label: '说明' },
]

const ruleColumns: Column[] = [
  { key: 'item', label: '收费项目' },
  { key: 'price', label: '单价(元/学分)', width: '130px', align: 'right' },
  { key: 'note', label: '说明' },
  { key: 'act', label: '操作', width: '90px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    await loadTerm()
    rules.value = await feeApi.rules()
    if (auth.isStudent) {
      bill.value = await feeApi.bill({ termId: currentTerm.value?.id })
    } else {
      bill.value = null
    }
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '收费信息加载失败'
  }
}
onMounted(load)

async function editRule(rule: FeeRule) {
  const raw = prompt(`修改「${rule.item}」的每学分单价（元）`, String(rule.creditPrice))
  if (raw === null) return
  const price = Number(raw)
  if (Number.isNaN(price) || price < 0) {
    toast('单价要填非负数字', 'bad')
    return
  }
  try {
    await feeApi.saveRule({ id: rule.id, item: rule.item, creditPrice: price, note: rule.note ?? '' })
    toast('已更新收费标准', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '保存失败', 'bad')
  }
}
</script>

<template>
  <Plate
    title="学分收费查询"
    :note="auth.isStudent
      ? `${bill?.termName ?? currentTerm?.name ?? ''}：重新修读与刷分重新修读按学分收费`
      : '收费单价由教务处维护；学生端只能看自己的账单'"
  >
    <template #actions>
      <Btn
        v-if="auth.isStudent"
        variant="quiet"
        @click="() => downloadCsv('/export/my-fee').then(() => toast('账单已导出', 'ok')).catch((e) => toast(e.message ?? '导出失败', 'bad'))"
      >
        导出账单
      </Btn>
    </template>
    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="没有收费信息"
      empty-detail="如果这里是空的，说明本学期没有需要缴费的课程。"
      :skeleton-rows="4"
      @retry="load"
    >
      <template v-if="auth.isStudent">
        <div class="summary">
          <div>
            <p class="summary__label">本学期应缴</p>
            <p class="summary__value num">{{ bill?.total ?? 0 }} 元</p>
          </div>
          <p class="summary__who">
            {{ bill?.studentName }}（{{ bill?.studentNo }}）　{{ bill?.termName ?? '' }}
          </p>
        </div>

        <DataTable :columns="billColumns" state="ready" min-width="980px">
          <tr v-for="(i, idx) in bill?.items ?? []" :key="idx">
            <td>{{ i.courseName }} <span class="dim">（{{ i.courseCode }}）</span></td>
            <td class="num num-end">{{ i.credit }}</td>
            <td>{{ i.item }}</td>
            <td class="num num-end">{{ i.unitPrice }}</td>
            <td class="num num-end">{{ i.amount }}</td>
            <td class="dim">{{ i.reason }}</td>
          </tr>
          <tr v-if="bill && !bill.items.length">
            <td colspan="6" class="none">本学期没有需要缴费的课程（首修不收费）。</td>
          </tr>
        </DataTable>

        <ul class="notes">
          <li v-for="(n, i) in bill?.notes ?? []" :key="i">{{ n }}</li>
        </ul>
      </template>

      <section class="block">
        <h3 class="block__title">收费项目与标准</h3>
        <DataTable :columns="ruleColumns" state="ready" min-width="760px">
          <tr v-for="r in rules" :key="r.id">
            <td>{{ r.item }}</td>
            <td class="num num-end">{{ r.creditPrice }}</td>
            <td class="dim">{{ r.note ?? '—' }}</td>
            <td class="rowact">
              <Btn v-if="!auth.isStudent" variant="quiet" @click="editRule(r)">改单价</Btn>
              <span v-else class="dim">—</span>
            </td>
          </tr>
        </DataTable>
        <p class="hint">
          手册第二十三条只写「按规定缴交」，并注明收费标准按学院有关规定执行——
          金额不在手册里，所以这里的单价是演示数据，实际以教务处通知为准。
        </p>
      </section>
    </StateHost>
  </Plate>
</template>

<style scoped>
.summary {
  display: flex;
  align-items: baseline;
  gap: var(--s-6);
  padding: var(--s-4);
  border-bottom: 1px solid var(--line);
}
.summary__label {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.summary__value {
  font-size: var(--t-xl);
  font-weight: 600;
}
.summary__who {
  font-size: var(--t-sm);
  color: var(--ink-muted);
}
.block {
  padding: var(--s-4) 0 0;
}
.block__title {
  font-size: var(--t-sm);
  font-weight: 600;
  padding: 0 var(--s-4) var(--s-2);
}
.hint {
  padding: var(--s-2) var(--s-4) 0;
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.notes {
  padding: var(--s-3) var(--s-4) 0 calc(var(--s-4) + 1.2em);
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.none {
  padding: var(--s-4);
  color: var(--ink-muted);
}
.dim {
  color: var(--ink-muted);
  font-size: var(--t-xs);
}
</style>
