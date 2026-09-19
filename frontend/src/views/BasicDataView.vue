<script setup lang="ts">
/** 基础数据：学院、专业、班级、学期。当前版本只读，维护走接口或后续版本。 */
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { basicApi } from '@/api'
import type { Clazz, College, Major, Term } from '@/api/types'
import Plate from '@/components/Plate.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const colleges = ref<College[]>([])
const majors = ref<Major[]>([])
const clazzes = ref<Clazz[]>([])
const terms = ref<Term[]>([])
const state = ref<'loading' | 'ready' | 'error'>('loading')
const errorDetail = ref('')

async function load() {
  state.value = 'loading'
  try {
    const [c, m, z, t] = await Promise.all([
      basicApi.colleges(),
      basicApi.majors(),
      basicApi.clazzes(),
      basicApi.terms(),
    ])
    colleges.value = c
    majors.value = m
    clazzes.value = z
    terms.value = t
    state.value = 'ready'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '基础数据加载失败'
  }
}
onMounted(load)
</script>

<template>
  <Plate title="基础数据" note="学院、专业、班级与学期，其余模块的下拉选项都来自这里">
    <p v-if="state === 'error'" class="err">{{ errorDetail }}</p>
    <div v-else-if="state === 'ready'" class="grid">
      <section class="block">
        <h3>学院</h3>
        <ul>
          <li v-for="c in colleges" :key="c.id"><span class="num">{{ c.code }}</span> {{ c.name }}</li>
        </ul>
      </section>
      <section class="block">
        <h3>专业</h3>
        <ul>
          <li v-for="m in majors" :key="m.id"><span class="num">{{ m.code }}</span> {{ m.name }}</li>
        </ul>
      </section>
      <section class="block">
        <h3>班级</h3>
        <ul>
          <li v-for="z in clazzes" :key="z.id"><span class="num">{{ z.code }}</span> {{ z.name }}</li>
        </ul>
      </section>
      <section class="block">
        <h3>学期</h3>
        <ul>
          <li v-for="t in terms" :key="t.id">
            <span class="num">{{ t.code }}</span> {{ t.name }}
            <StatusPlate v-if="t.isCurrent === 1" value="生效" />
          </li>
        </ul>
      </section>
    </div>
    <p class="foot">
      维护操作请通过接口或后续版本的管理界面完成；当前版本仅提供查询，
      避免误改基础编码影响已有选课记录。
    </p>
  </Plate>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.block {
  padding: var(--s-4);
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.block:nth-child(even) {
  border-right: 0;
}
.block h3 {
  font-size: var(--t-sm);
  font-weight: 600;
  margin-bottom: var(--s-2);
}
.block ul {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: var(--t-sm);
}
.block li {
  padding: var(--s-1) 0;
  border-bottom: 1px solid var(--line);
}
.block li:last-child {
  border-bottom: 0;
}
.block .num {
  color: var(--accent-deep);
  margin-right: var(--s-2);
  font-size: var(--t-xs);
}
.foot {
  padding: var(--s-4);
  font-size: var(--t-xs);
  color: var(--ink-muted);
  max-width: 60ch;
}
.err {
  padding: var(--s-6);
  color: var(--bad);
  font-size: var(--t-sm);
}
@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .block {
    border-right: 0;
  }
}
</style>
