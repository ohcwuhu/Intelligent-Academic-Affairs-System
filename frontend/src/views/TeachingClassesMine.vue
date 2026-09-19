<script setup lang="ts">
/** 教师的教学班列表。数据范围由服务端按令牌中的教师身份限定。 */
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { teachingClassApi } from '@/api'
import type { TeachingClassVO } from '@/api/types'
import { creditText } from '@/utils/format'
import { useCurrentTerm } from '@/components/useTerm'
import Plate from '@/components/Plate.vue'
import StateHost from '@/components/StateHost.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const { currentTerm, load: loadTerm } = useCurrentTerm()
const list = ref<TeachingClassVO[]>([])
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')

async function load() {
  state.value = 'loading'
  try {
    await loadTerm()
    list.value = await teachingClassApi.list({ termId: currentTerm.value?.id })
    state.value = list.value.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '教学班加载失败'
  }
}

onMounted(load)

const totals = computed(() => ({
  classes: list.value.length,
  students: list.value.reduce((s, c) => s + c.enrolled, 0),
}))
</script>

<template>
  <Plate
    title="我的教学班"
    :note="`${currentTerm?.name ?? ''}，${totals.classes} 个教学班，${totals.students} 名学生`"
  >
    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="本学期没有教学班"
      empty-detail="教务排课完成后，你任教的班级会出现在这里。"
      :skeleton-rows="4"
      @retry="load"
    >
      <ul class="cards">
        <li v-for="c in list" :key="c.id" class="card">
          <div class="card__code">
            <span class="num">{{ c.courseCode }}</span>
            <span class="card__seq num">{{ c.code }}</span>
          </div>
          <h3 class="card__name">{{ c.courseName }}</h3>
          <dl class="card__meta">
            <div><dt>上课时间</dt><dd>{{ c.timeText }}</dd></div>
            <div><dt>地点</dt><dd>{{ c.classroom || '待定' }}</dd></div>
            <div><dt>学分</dt><dd class="num">{{ creditText(c.credit) }}</dd></div>
            <div><dt>选课人数</dt><dd class="num">{{ c.enrolled }} / {{ c.capacity }}</dd></div>
            <div><dt>状态</dt><dd><StatusPlate :value="c.status" /></dd></div>
          </dl>
          <RouterLink class="card__go" :to="`/teach/classes/${c.id}`">
            查看名单并录入成绩
          </RouterLink>
        </li>
      </ul>
    </StateHost>
  </Plate>
</template>

<style scoped>
.cards {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
}

.card {
  padding: var(--s-4);
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: var(--s-2);
}

.card__code {
  display: flex;
  align-items: baseline;
  gap: var(--s-2);
}

.card__code > .num {
  font-size: var(--t-sm);
  font-weight: 600;
  color: var(--accent-deep);
}

.card__seq {
  font-size: 10px;
  color: var(--ink-muted);
}

.card__name {
  font-size: var(--t-md);
  font-weight: 600;
}

.card__meta {
  margin: var(--s-2) 0 0;
  display: grid;
  gap: var(--s-1);
}

.card__meta > div {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: var(--s-2);
}

.card__meta dt {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}

.card__meta dd {
  margin: 0;
  font-size: var(--t-sm);
}

.card__go {
  margin-top: auto;
  padding-top: var(--s-3);
  font-size: var(--t-sm);
  color: var(--accent-deep);
}
</style>
