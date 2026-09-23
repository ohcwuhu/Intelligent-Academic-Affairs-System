<script setup lang="ts">
/**
 * 选课。
 *
 * 冲突判定直接问服务端，不在前端重算。之前这里用星期加节次做了个前端预判，
 * 结果把「操作系统（单周）」与「人工智能基础（双周）」判成了冲突，
 * 而这门课按单双周互补根本不算冲突。业务规则只能有一处实现。
 */
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { enrollmentApi, teachingClassApi } from '@/api'
import type { PlanHint } from '@/api/types'
import type { ConflictItem, MyCourse, TeachingClassVO } from '@/api/types'
import { creditText } from '@/utils/format'
import { useCurrentTerm } from '@/components/useTerm'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import TapeMark from '@/components/TapeMark.vue'
import StateHost from '@/components/StateHost.vue'
import FieldRow from '@/components/FieldRow.vue'

const { currentTerm, load: loadTerm } = useCurrentTerm()

const list = ref<TeachingClassVO[]>([])
const mine = ref<MyCourse[]>([])
const conflicts = ref<Map<number, ConflictItem[]>>(new Map())
const hints = ref<Map<number, PlanHint>>(new Map())
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const keyword = ref('')
const onlyAvailable = ref(false)
const busyId = ref<number | null>(null)

async function load() {
  state.value = 'loading'
  try {
    await loadTerm()
    const termId = currentTerm.value?.id
    const [classes, my] = await Promise.all([
      teachingClassApi.selectable({ termId }),
      enrollmentApi.my(termId),
    ])
    list.value = classes
    mine.value = my
    // 冲突判定问服务端：界面不推算业务规则
    const previews = await Promise.all(
      classes.map((c) => enrollmentApi.preview(c.id).catch(() => [] as ConflictItem[])),
    )
    conflicts.value = new Map(classes.map((c, i) => [c.id, previews[i]]))
    // 培养计划提示：这门课算不算毕业学分、以前修过没有
    const planHints = await enrollmentApi.planHints(termId).catch(() => [] as PlanHint[])
    hints.value = new Map(planHints.map((h) => [h.courseId, h]))
    state.value = classes.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '课程列表加载失败'
  }
}

onMounted(load)

const myClassIds = computed(() => new Set(mine.value.map((c) => c.teachingClassId)))
const filtered = computed(() =>
  list.value.filter((c) => {
    if (onlyAvailable.value && c.remaining <= 0) return false
    const k = keyword.value.trim()
    if (!k) return true
    return c.courseName.includes(k) || c.courseCode.includes(k) || (c.teacherName ?? '').includes(k)
  }),
)
const pickedCredit = computed(() => mine.value.reduce((s, c) => s + (c.credit ?? 0), 0))

function conflictOf(id: number): ConflictItem | null {
  const items = conflicts.value.get(id)
  return items && items.length ? items[0] : null
}

/** 计划提示文案：先说算不算毕业学分，再说修过没有。 */
function planText(courseId: number): string {
  const h = hints.value.get(courseId)
  if (!h) return ''
  const parts: string[] = []
  if (h.inPlan) {
    parts.push(`计划内 · ${h.module ?? '未分模块'}${h.planTerm ? ` · 第${h.planTerm}学期` : ''}`)
  } else {
    // 不写"不算毕业学分"：对不上可能只是方案里的课程名与课程库不同名，
    // 断言成"不算数"会误导学生，如实说清依据不足即可
    parts.push('未在培养方案中找到同名课程，学分归属请咨询教务处')
  }
  if (h.failedScore != null) {
    parts.push(`以前 ${h.failedScore} 分未通过，本次属重新修读`)
  } else if (h.passedScore != null) {
    parts.push(`以前 ${h.passedScore} 分已通过，本次属刷分重新修读`)
  }
  return parts.join('　')
}

async function choose(c: TeachingClassVO) {
  busyId.value = c.id
  try {
    const res = await enrollmentApi.select(c.id)
    // 冲突在列表里就已经封条拦住，接口也会再拦一次；能走到这里就是真的选上了
    toast(res.message || '选课成功', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '选课失败', 'bad')
  } finally {
    busyId.value = null
  }
}

async function drop(c: MyCourse) {
  busyId.value = c.teachingClassId
  try {
    await enrollmentApi.drop(c.enrollmentId)
    toast(`已退选 ${c.courseName}`, 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '退课失败', 'bad')
  } finally {
    busyId.value = null
  }
}
</script>

<template>
  <Plate title="选课" :note="`${currentTerm?.name ?? ''}，可选 ${list.length} 个教学班`">
    <div class="bar">
      <FieldRow label="查找课程" for-id="kw" hint="课程名称、课程代码或教师姓名">
        <input id="kw" v-model="keyword" placeholder="例如 数据结构 或 CS102" />
      </FieldRow>
      <label class="check">
        <input v-model="onlyAvailable" type="checkbox" />
        <span>只看还有名额的</span>
      </label>
      <p class="bar__stat num">已选 {{ mine.length }} 门 · {{ creditText(pickedCredit) }} 学分</p>
    </div>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="当前学期还没有开放的教学班"
      empty-detail="教务发布开课计划后，课程会出现在这里。"
      :skeleton-rows="6"
      @retry="load"
    >
      <ul class="rows">
        <li
          v-for="c in filtered"
          :key="c.id"
          class="row"
          :class="{ 'row--mine': myClassIds.has(c.id) }"
        >
          <div class="row__code">
            <span class="num">{{ c.courseCode }}</span>
            <span class="row__seq num">{{ c.code }}</span>
          </div>
          <div class="row__main">
            <p class="row__name">{{ c.courseName }}</p>
            <p class="row__meta">
              {{ c.courseType }} · {{ creditText(c.credit) }} 学分 · {{ c.teacherName ?? '教师待定' }}
            </p>
            <p v-if="planText(c.courseId)" class="row__plan" :class="{ 'is-out': !hints.get(c.courseId)?.inPlan }">
              {{ planText(c.courseId) }}
            </p>
          </div>
          <div class="row__when">
            <p>{{ c.timeText }}</p>
            <p class="row__room">{{ c.classroom || '地点待定' }}</p>
          </div>
          <div class="row__seat">
            <p class="num row__remain" :class="{ 'is-zero': c.remaining <= 0 }">{{ c.remaining }}</p>
            <p class="row__seat-label">剩余 / {{ c.capacity }}</p>
          </div>
          <div class="row__act">
            <template v-if="myClassIds.has(c.id)">
              <span class="row__picked">已选</span>
              <Btn
                variant="quiet"
                :loading="busyId === c.id"
                @click="drop(mine.find((m) => m.teachingClassId === c.id)!)"
              >
                退选
              </Btn>
            </template>
            <template v-else-if="c.remaining <= 0">
              <TapeMark text="名额已满" />
            </template>
            <template v-else-if="conflictOf(c.id)">
              <TapeMark :text="`与 ${conflictOf(c.id)!.courseB} 冲突`" />
            </template>
            <Btn v-else variant="solid" :loading="busyId === c.id" @click="choose(c)">选课</Btn>
          </div>
        </li>
      </ul>
      <p v-if="!filtered.length" class="none">没有符合条件的课程，换个关键词试试。</p>
    </StateHost>
  </Plate>
</template>

<style scoped>
.bar {
  display: grid;
  grid-template-columns: minmax(200px, 320px) auto 1fr;
  align-items: end;
  gap: var(--s-6);
  padding: var(--s-4);
  border-bottom: 1px solid var(--line);
}

.check {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  font-size: var(--t-sm);
  height: 30px;
}

.bar__stat {
  justify-self: end;
  font-size: var(--t-sm);
  color: var(--ink-muted);
}

.rows {
  list-style: none;
  margin: 0;
  padding: 0;
}

.row {
  display: grid;
  grid-template-columns: 130px minmax(0, 1.4fr) minmax(0, 1.2fr) 92px 168px;
  align-items: center;
  gap: var(--s-4);
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
}

.row:last-child {
  border-bottom: 0;
}

.row:hover {
  background: var(--face);
}

.row--mine {
  box-shadow: inset 0 0 0 1px var(--ok);
}

.row__code {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.row__code > .num {
  font-size: var(--t-sm);
  font-weight: 600;
}

.row__seq {
  font-size: 10px;
  color: var(--ink-muted);
}

.row__name {
  font-size: var(--t-base);
  font-weight: 600;
}

.row__meta,
.row__room {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}

/* 计划提示：这是选课时的决策信息，用青铜色小字，不要抢课程名 */
.row__plan {
  margin-top: 2px;
  font-size: var(--t-xs);
  color: var(--accent-deep);
}
.row__plan.is-out {
  color: var(--ink-muted);
}

.row__when p:first-child {
  font-size: var(--t-sm);
}

.row__seat {
  text-align: right;
}

.row__remain {
  font-size: var(--t-md);
  font-weight: 600;
}

.row__remain.is-zero {
  color: var(--bad);
}

.row__seat-label {
  font-size: 10px;
  color: var(--ink-muted);
}

.row__act {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--s-2);
  min-height: 30px;
  /* 胶带是绝对定位铺满这个盒子的，盒子必须有确定宽度，
     否则 auto 宽会被算成 0，胶带就会溢出到行外。 */
  min-width: 152px;
}

.row__picked {
  font-size: var(--t-xs);
  color: var(--ok);
  font-weight: 600;
}

.none {
  padding: var(--s-6) var(--s-4);
  font-size: var(--t-sm);
  color: var(--ink-muted);
}

@media (max-width: 1180px) {
  .row {
    grid-template-columns: 118px minmax(0, 1fr) 92px 168px;
  }
  .row__when {
    grid-column: 2 / 3;
  }
}

@media (max-width: 900px) {
  .bar {
    grid-template-columns: 1fr;
    gap: var(--s-3);
  }
  .bar__stat {
    justify-self: start;
  }
  .row {
    grid-template-columns: 1fr auto;
    gap: var(--s-2) var(--s-3);
  }
  .row__main,
  .row__when,
  .row__seat {
    grid-column: 1 / 2;
    text-align: left;
  }
  .row__seat {
    display: flex;
    gap: var(--s-2);
    align-items: baseline;
  }
  .row__act {
    grid-column: 2 / 3;
    grid-row: 1 / 2;
  }
}
</style>
