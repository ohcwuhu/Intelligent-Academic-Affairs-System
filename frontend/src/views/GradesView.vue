<script setup lang="ts">
/**
 * 成绩与学分。
 *
 * 页面上的每个数字都直接来自服务端汇总，界面不做任何折算。
 * 重修记录原样列出，但学分只计一次，处理逻辑在服务端。
 */
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { enrollmentApi, gradeComponentApi } from '@/api'
import { downloadCsv } from '@/api/client'
import type { CourseComponents, CreditSummary, MyCourse } from '@/api/types'
import { creditText, gpaText, scoreText } from '@/utils/format'
import { toast } from '@/components/useToast'
import Btn from '@/components/Btn.vue'
import Plate from '@/components/Plate.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'

const courses = ref<MyCourse[]>([])
const summary = ref<CreditSummary | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

async function load() {
  state.value = 'loading'
  try {
    const [list, sum] = await Promise.all([enrollmentApi.my(), enrollmentApi.summary()])
    courses.value = list
    summary.value = sum
    state.value = list.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '成绩加载失败'
  }
}

onMounted(async () => {
  await load()
  // 成绩构成是这一页的一部分，进页面就带上；有就显示，没有不占位
  await loadComponents()
})

const byTerm = computed(() => {
  const map = new Map<string, MyCourse[]>()
  for (const c of courses.value) {
    const key = c.termName ?? '未知学期'
    map.set(key, [...(map.get(key) ?? []), c])
  }
  return [...map.entries()]
})

const columns: Column[] = [
  { key: 'code', label: '课程代码', width: '120px' },
  { key: 'name', label: '课程名称' },
  { key: 'type', label: '性质', width: '80px' },
  { key: 'credit', label: '学分', width: '70px', align: 'right' },
  { key: 'score', label: '成绩', width: '80px', align: 'right' },
  { key: 'gp', label: '绩点', width: '80px', align: 'right' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'component', label: '成绩构成', width: '150px' },
]

/** 分项构成：平时/期中/期末。按需拉取，不在首屏加请求。 */
const components = ref<Map<string, CourseComponents>>(new Map())
const componentError = ref('')

async function loadComponents() {
  componentError.value = ''
  try {
    const list = await gradeComponentApi.mine()
    components.value = new Map(list.map((c) => [`${c.courseCode}-${c.termName}`, c]))
  } catch (e) {
    componentError.value = e instanceof ApiError ? e.message : '成绩构成加载失败'
  }
}

function componentsOf(c: { courseCode: string; termName?: string | null }) {
  return components.value.get(`${c.courseCode}-${c.termName ?? null}`)?.items ?? []
}
</script>

<template>
  <Plate title="成绩与学分" note="成绩由任课教师录入，学分与绩点由服务端按分段表计算">
    <template #actions>
      <Btn
        variant="quiet"
        @click="() => downloadCsv('/export/my-grades').then(() => toast('成绩已导出', 'ok')).catch((e) => toast(e.message ?? '导出失败', 'bad'))"
      >
        导出成绩
      </Btn>
    </template>
    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="还没有修读记录"
      empty-detail="选课并录入成绩之后，这里会显示每门课的学分与绩点。"
      :skeleton-rows="6"
      @retry="load"
    >
      <dl v-if="summary" class="ledger">
        <div class="ledger__cell">
          <dt>已获学分</dt>
          <dd class="num">{{ creditText(summary.earnedCredit) }}</dd>
          <p class="ledger__why">成绩达到 60 分的课程学分之和</p>
        </div>
        <div class="ledger__cell">
          <dt>在修学分</dt>
          <dd class="num">{{ creditText(summary.inProgressCredit) }}</dd>
          <p class="ledger__why">已选但尚未出成绩</p>
        </div>
        <div class="ledger__cell">
          <dt>平均学分绩点</dt>
          <dd class="num">{{ gpaText(summary.gpa) }}</dd>
          <p class="ledger__why">按学分加权，重修取最高一次</p>
        </div>
        <div class="ledger__cell">
          <dt>课程门数</dt>
          <dd class="num">{{ summary.passedCourses }} / {{ summary.failedCourses }}</dd>
          <p class="ledger__why">通过门数 / 未通过门数</p>
        </div>
      </dl>

      <section v-for="[term, list] in byTerm" :key="term" class="term">
        <header class="term__head">
          <h3 class="term__name">{{ term }}</h3>
          <p class="term__sum num">
            {{ list.length }} 门 ·
            通过 {{ list.filter((c) => c.score !== null && c.score >= 60).length }} 门
          </p>
        </header>
        <DataTable :columns="columns" state="ready" min-width="700px">
          <tr v-for="c in list" :key="c.enrollmentId">
            <td><span class="num">{{ c.courseCode }}</span></td>
            <td>{{ c.courseName }}</td>
            <td>{{ c.courseType }}</td>
            <td class="num num-end">{{ creditText(c.credit) }}</td>
            <td class="num num-end" :class="{ 'is-fail': c.score !== null && c.score < 60 }">
              {{ scoreText(c.score) }}
            </td>
            <td class="num num-end">{{ c.gradePoint === null ? '-' : gpaText(c.gradePoint) }}</td>
            <td>{{ c.scoreStatus }}</td>
            <td>
              <template v-if="componentsOf(c).length">
                <span v-for="x in componentsOf(c)" :key="x.item" class="part">
                  {{ x.item }}
                  <span class="num">{{ x.score ?? '—' }}</span>
                  <span v-if="x.weight != null" class="dim">×{{ x.weight }}%</span>
                </span>
              </template>
              <Btn v-else variant="quiet" @click="loadComponents">看构成</Btn>
            </td>
          </tr>
        </DataTable>
      </section>
    </StateHost>
  </Plate>
</template>

<style scoped>
.ledger {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
}

.ledger__cell {
  padding: var(--s-4);
  border-right: 1px solid var(--line);
}

.ledger__cell:last-child {
  border-right: 0;
}

.ledger__cell dt {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}

.ledger__cell dd {
  margin: var(--s-1) 0 0;
  font-size: var(--t-xl);
  font-weight: 600;
  line-height: 1.1;
}

.ledger__why {
  margin-top: var(--s-1);
  font-size: 11px;
  color: var(--ink-muted);
}

.term {
  border-top: 1px solid var(--line);
}

.term__head {
  display: flex;
  align-items: baseline;
  gap: var(--s-4);
  padding: var(--s-3) var(--s-4);
}

.term__name {
  font-size: var(--t-base);
  font-weight: 600;
}

.term__sum {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}

.is-fail {
  color: var(--bad);
  font-weight: 600;
}

@media (max-width: 900px) {
  .ledger {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .ledger__cell:nth-child(2) {
    border-right: 0;
  }
  .ledger__cell:nth-child(1),
  .ledger__cell:nth-child(2) {
    border-bottom: 1px solid var(--line);
  }
}
</style>
