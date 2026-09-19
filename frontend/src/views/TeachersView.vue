<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '@/api/client'
import { teacherApi } from '@/api'
import type { Teacher } from '@/api/types'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const rows = ref<Teacher[]>([])
const total = ref(0)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const page = ref(1)
const size = 10
const filters = reactive({ keyword: '' })
const editing = ref<Record<string, unknown> | null>(null)
const formError = ref('')

const columns: Column[] = [
  { key: 'no', label: '工号', width: '110px' },
  { key: 'name', label: '姓名', width: '110px' },
  { key: 'gender', label: '性别', width: '60px' },
  { key: 'title', label: '职称', width: '100px' },
  { key: 'college', label: '所属学院' },
  { key: 'phone', label: '联系电话', width: '140px' },
  { key: 'status', label: '状态', width: '80px' },
  { key: 'act', label: '操作', width: '90px', align: 'right' },
]

const collegeName = (id: number) =>
  id === 1 ? '计算机与信息科学系' : id === 2 ? '电子工程系' : '经济管理系'

async function load() {
  state.value = 'loading'
  try {
    const res = await teacherApi.page({
      page: page.value,
      size,
      keyword: filters.keyword || undefined,
    })
    rows.value = res.records
    total.value = res.total
    state.value = res.records.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '教师档案加载失败'
  }
}
onMounted(load)

async function save() {
  const f = editing.value
  if (!f) return
  if (!f.teacherNo || !f.name) {
    formError.value = '工号与姓名不能为空'
    return
  }
  try {
    await teacherApi.save(f)
    toast('已保存', 'ok')
    editing.value = null
    await load()
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : '保存失败'
  }
}
</script>

<template>
  <Plate title="教师档案" :note="`共 ${total} 人`">
    <template #actions>
      <Btn
        variant="solid"
        @click="
          formError = '';
          editing = { id: null, teacherNo: '', name: '', gender: '男', title: '讲师',
                      collegeId: 1, phone: '', email: '', status: '在职' }
        "
      >
        新增教师
      </Btn>
    </template>

    <form v-if="editing" class="editor" @submit.prevent="save">
      <p class="editor__title">{{ editing.id ? '修改教师' : '新增教师' }}</p>
      <div class="editor__grid">
        <FieldRow label="工号"><input v-model="editing.teacherNo" /></FieldRow>
        <FieldRow label="姓名"><input v-model="editing.name" /></FieldRow>
        <FieldRow label="性别">
          <select v-model="editing.gender"><option>男</option><option>女</option></select>
        </FieldRow>
        <FieldRow label="职称">
          <select v-model="editing.title">
            <option>教授</option><option>副教授</option><option>讲师</option><option>助教</option>
          </select>
        </FieldRow>
        <FieldRow label="所属学院">
          <select v-model.number="editing.collegeId">
            <option :value="1">计算机与信息科学系</option>
            <option :value="2">电子工程系</option>
            <option :value="3">经济管理系</option>
          </select>
        </FieldRow>
        <FieldRow label="联系电话"><input v-model="editing.phone" class="num" /></FieldRow>
        <FieldRow label="电子邮箱"><input v-model="editing.email" class="num" /></FieldRow>
        <FieldRow label="状态">
          <select v-model="editing.status"><option>在职</option><option>离职</option></select>
        </FieldRow>
      </div>
      <p v-if="formError" class="editor__err">{{ formError }}</p>
      <div class="editor__act">
        <Btn variant="solid" type="submit">保存</Btn>
        <Btn @click="editing = null">取消</Btn>
      </div>
    </form>

    <form class="filters" @submit.prevent="((page = 1), load())">
      <FieldRow label="查找"><input v-model="filters.keyword" placeholder="工号或姓名" /></FieldRow>
      <Btn variant="solid" type="submit">查询</Btn>
    </form>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="没有符合条件的教师"
      empty-detail="换个关键词，或者新增一条档案。"
      :skeleton-rows="6"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="900px">
        <tr v-for="t in rows" :key="t.id">
          <td><span class="num">{{ t.teacherNo }}</span></td>
          <td>{{ t.name }}</td>
          <td>{{ t.gender }}</td>
          <td>{{ t.title ?? '-' }}</td>
          <td>{{ collegeName(t.collegeId) }}</td>
          <td class="num">{{ t.phone ?? '-' }}</td>
          <td><StatusPlate :value="t.status" /></td>
          <td class="rowact"><Btn variant="quiet" @click="editing = { ...t }">修改</Btn></td>
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
