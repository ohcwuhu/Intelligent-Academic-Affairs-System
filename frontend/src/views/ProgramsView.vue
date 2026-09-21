<script setup lang="ts">
/**
 * 培养方案（教务侧）。
 *
 * 只读：方案是学校定的，教务在这里看的是"系统里现在按哪一版审"，
 * 以及每个模块要求多少学分、计划开了哪些课。导入在「数据导入」里做。
 */
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { programApi } from '@/api'
import type { ProgramDetail, ProgramRow } from '@/api/types'
import { creditText } from '@/utils/format'
import Plate from '@/components/Plate.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const programs = ref<ProgramRow[]>([])
const detail = ref<ProgramDetail | null>(null)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const moduleFilter = ref('')

const moduleColumns: Column[] = [
  { key: 'category', label: '课程模块' },
  { key: 'hours', label: '学时/周数', width: '120px' },
  { key: 'credit', label: '要求学分', width: '100px', align: 'right' },
  { key: 'ratio', label: '占比', width: '90px', align: 'right' },
]

const courseColumns: Column[] = [
  { key: 'term', label: '学期', width: '70px', align: 'right' },
  { key: 'name', label: '课程名称' },
  { key: 'code', label: '课程码', width: '100px' },
  { key: 'group', label: '方向/分组', width: '180px' },
  { key: 'assess', label: '考核', width: '70px' },
  { key: 'credit', label: '学分', width: '70px', align: 'right' },
  { key: 'hours', label: '学时', width: '70px', align: 'right' },
  { key: 'week', label: '周学时', width: '80px', align: 'right' },
  { key: 'note', label: '备注' },
]

async function load() {
  state.value = 'loading'
  try {
    programs.value = await programApi.list()
    if (programs.value.length) {
      await open(programs.value[0].id)
    }
    state.value = programs.value.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '培养方案加载失败'
  }
}
onMounted(load)

async function open(id: number) {
  try {
    detail.value = await programApi.detail(id)
    moduleFilter.value = ''
  } catch (e) {
    errorDetail.value = e instanceof ApiError ? e.message : '方案详情加载失败'
  }
}

const shownCourses = computed(() =>
  (detail.value?.courses ?? []).filter((c) => !moduleFilter.value || c.module === moduleFilter.value),
)
</script>

<template>
  <Plate
    title="培养方案"
    note="毕业审核按这里的现行方案计算；方案由「数据导入」导入，不在页面上手工填"
  >
    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="系统里还没有培养方案"
      empty-detail="到「数据导入」里选「培养方案」上传学校给的 Excel。"
      :skeleton-rows="5"
      @retry="load"
    >
      <div class="picker">
        <button
          v-for="p in programs"
          :key="p.id"
          type="button"
          class="picker__item"
          :class="{ 'is-here': detail?.program.id === p.id }"
          @click="open(p.id)"
        >
          <span class="picker__name">{{ p.majorName }}</span>
          <span class="picker__meta">
            <StatusPlate :value="p.status" />
            <span class="num">{{ creditText(p.minCredit) }}</span> 学分 ·
            {{ p.moduleCount }} 模块 · {{ p.courseCount }} 门课
          </span>
        </button>
      </div>

      <template v-if="detail">
        <dl class="ledger">
          <div><dt>方案</dt><dd>{{ detail.program.title }}</dd></div>
          <div><dt>学制</dt><dd>{{ detail.program.duration ?? '—' }}</dd></div>
          <div><dt>授予学位</dt><dd>{{ detail.program.degree ?? '—' }}</dd></div>
          <div><dt>毕业最低学分</dt><dd class="num">{{ creditText(detail.program.minCredit) }}</dd></div>
          <div><dt>计划内学分合计</dt><dd class="num">{{ creditText(detail.program.courseCreditSum) }}</dd></div>
          <div><dt>导入时间</dt><dd class="num">{{ detail.program.importedAt ?? '—' }}</dd></div>
        </dl>
        <p v-if="detail.program.sourceNote" class="source">{{ detail.program.sourceNote }}</p>

        <section class="block">
          <h3 class="block__title">学分结构</h3>
          <DataTable :columns="moduleColumns" state="ready" min-width="700px">
            <tr
              v-for="m in detail.modules"
              :key="m.category"
              class="clickable"
              :class="{ 'is-here': moduleFilter === m.category }"
              @click="moduleFilter = moduleFilter === m.category ? '' : m.category"
            >
              <td>{{ m.category }}</td>
              <td>{{ m.hoursText ?? '—' }}</td>
              <td class="num num-end">{{ creditText(m.credit) }}</td>
              <td class="num num-end">{{ m.ratio == null ? '—' : (m.ratio * 100).toFixed(1) + '%' }}</td>
            </tr>
          </DataTable>
          <p class="hint">点一行只看该模块的课；再点一次取消筛选。</p>
        </section>

        <section class="block">
          <h3 class="block__title">
            计划课程（{{ shownCourses.length }} 门{{ moduleFilter ? '，已筛' + moduleFilter : '' }}）
          </h3>
          <DataTable :columns="courseColumns" state="ready" min-width="1080px">
            <tr v-for="c in shownCourses" :key="`${c.module}-${c.courseName}-${c.termNo ?? 0}`">
              <td class="num num-end">{{ c.termNo ?? '—' }}</td>
              <td>{{ c.courseName }}</td>
              <td>
                <span v-if="c.courseCode" class="num code">{{ c.courseCode }}</span>
                <span v-else class="dim">未对齐</span>
              </td>
              <td class="dim">{{ c.groupName ?? '—' }}</td>
              <td>{{ c.assessType ?? '—' }}</td>
              <td class="num num-end">{{ creditText(c.credit) }}</td>
              <td class="num num-end">{{ c.totalHours ?? '—' }}</td>
              <td class="num num-end">{{ c.weekHours ?? '—' }}</td>
              <td class="dim">{{ c.note ?? '—' }}</td>
            </tr>
          </DataTable>
        </section>
      </template>
    </StateHost>
  </Plate>
</template>

<style scoped>
.picker {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s-2);
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
}
.picker__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--s-2) var(--s-3);
  background: var(--face);
  border: 1px solid var(--line-strong);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.picker__item.is-here {
  border-color: var(--structure);
  box-shadow: inset 3px 0 0 var(--accent);
}
.picker__name {
  font-size: var(--t-sm);
  font-weight: 600;
}
.picker__meta {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.ledger {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s-6);
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
}
.ledger dt {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.ledger dd {
  margin: 2px 0 0;
  font-size: var(--t-sm);
}
.source {
  padding: var(--s-2) var(--s-4);
  border-bottom: 1px solid var(--line);
  background: var(--face);
  font-size: var(--t-xs);
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
.clickable {
  cursor: pointer;
}
.is-here td {
  background: var(--face);
}
.hint {
  padding: var(--s-2) var(--s-4) 0;
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
/* 课程码用铭牌样式：它是课程在系统里的唯一标识，值得一眼认出来 */
.code {
  display: inline-block;
  padding: 0 5px;
  background: var(--structure);
  color: var(--face);
  font-size: var(--t-xs);
}
.dim {
  color: var(--ink-muted);
  font-size: var(--t-xs);
}
</style>
