<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '@/api/client'
import { courseApi } from '@/api'
import type { Course } from '@/api/types'
import { creditText } from '@/utils/format'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'

const rows = ref<Course[]>([])
const total = ref(0)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const page = ref(1)
const size = 10
const filters = reactive({ keyword: '', courseType: '' })
const editing = ref<Record<string, unknown> | null>(null)
const formError = ref('')

const columns: Column[] = [
  { key: 'code', label: '课程代码', width: '120px' },
  { key: 'name', label: '课程名称' },
  { key: 'type', label: '性质', width: '80px' },
  { key: 'credit', label: '学分', width: '70px', align: 'right' },
  { key: 'hours', label: '学时', width: '70px', align: 'right' },
  { key: 'assess', label: '考核方式', width: '90px' },
  { key: 'act', label: '操作', width: '90px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    const res = await courseApi.page({
      page: page.value,
      size,
      keyword: filters.keyword || undefined,
      courseType: filters.courseType || undefined,
    })
    rows.value = res.records
    total.value = res.total
    state.value = res.records.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '课程库加载失败'
  }
}
onMounted(load)

async function save() {
  const f = editing.value
  if (!f) return
  if (!f.code || !f.name) {
    formError.value = '课程代码与名称不能为空'
    return
  }
  if (!f.credit || Number(f.credit) <= 0) {
    formError.value = '学分必须大于 0'
    return
  }
  try {
    await courseApi.save(f)
    toast('已保存', 'ok')
    editing.value = null
    await load()
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : '保存失败'
  }
}
</script>

<template>
  <Plate title="课程库" :note="`共 ${total} 门课程`">
    <template #actions>
      <Btn
        variant="solid"
        @click="
          formError = '';
          editing = { id: null, code: '', name: '', credit: 2, hours: 32,
                      courseType: '必修', collegeId: 1, assessType: '考试', status: 1 }
        "
      >
        新增课程
      </Btn>
    </template>

    <form v-if="editing" class="editor" @submit.prevent="save">
      <p class="editor__title">{{ editing.id ? '修改课程' : '新增课程' }}</p>
      <div class="editor__grid">
        <FieldRow label="课程代码"><input v-model="editing.code" class="num" /></FieldRow>
        <FieldRow label="课程名称"><input v-model="editing.name" /></FieldRow>
        <FieldRow label="学分"><input v-model.number="editing.credit" type="number" step="0.5" class="num" /></FieldRow>
        <FieldRow label="总学时"><input v-model.number="editing.hours" type="number" class="num" /></FieldRow>
        <FieldRow label="课程性质">
          <select v-model="editing.courseType">
            <option>必修</option><option>选修</option><option>公选</option><option>实践</option>
          </select>
        </FieldRow>
        <FieldRow label="考核方式">
          <select v-model="editing.assessType"><option>考试</option><option>考查</option></select>
        </FieldRow>
        <FieldRow label="开课学院">
          <select v-model.number="editing.collegeId">
            <option :value="1">计算机与信息科学系</option>
            <option :value="2">电子工程系</option>
            <option :value="3">经济管理系</option>
          </select>
        </FieldRow>
      </div>
      <p v-if="formError" class="editor__err">{{ formError }}</p>
      <div class="editor__act">
        <Btn variant="solid" type="submit">保存</Btn>
        <Btn @click="editing = null">取消</Btn>
      </div>
    </form>

    <form class="filters" @submit.prevent="((page = 1), load())">
      <FieldRow label="查找"><input v-model="filters.keyword" placeholder="课程代码或名称" /></FieldRow>
      <FieldRow label="性质">
        <select v-model="filters.courseType">
          <option value="">全部</option>
          <option>必修</option><option>选修</option><option>公选</option><option>实践</option>
        </select>
      </FieldRow>
      <Btn variant="solid" type="submit">查询</Btn>
    </form>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="课程库还是空的"
      empty-detail="先新增一门课程，之后才能在开课时引用它。"
      :skeleton-rows="6"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="860px">
        <tr v-for="c in rows" :key="c.id">
          <td><span class="num">{{ c.code }}</span></td>
          <td>{{ c.name }}</td>
          <td>{{ c.courseType }}</td>
          <td class="num num-end">{{ creditText(c.credit) }}</td>
          <td class="num num-end">{{ c.hours }}</td>
          <td>{{ c.assessType }}</td>
          <td class="rowact"><Btn variant="quiet" @click="editing = { ...c }">修改</Btn></td>
        </tr>
      </DataTable>
      <div class="pager">
        <Btn :disabled="page <= 1" @click="((page -= 1), load())">上一页</Btn>
        <span class="pager__now num">第 {{ page }} 页 / 共 {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
        <Btn :disabled="page >= Math.ceil(total / size)" @click="((page += 1), load())">下一页</Btn>
      </div>
    </StateHost>
  </Plate>
</template>
