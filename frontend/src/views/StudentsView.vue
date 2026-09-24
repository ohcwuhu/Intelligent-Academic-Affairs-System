<script setup lang="ts">
/** 学生档案维护。数据范围由服务端按角色限定。 */
import { onMounted, reactive, ref } from 'vue'
import { ApiError, downloadCsv } from '@/api/client'
import { basicApi, studentApi } from '@/api'
import type { Clazz, StudentVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/components/useToast'
import { confirmDialog } from '@/components/useDialog'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const auth = useAuthStore()
const rows = ref<StudentVO[]>([])
const clazzes = ref<Clazz[]>([])
const total = ref(0)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const page = ref(1)
const size = 10
const filters = reactive({ keyword: '', clazzId: '', status: '' })
const editing = ref<Record<string, unknown> | null>(null)
const formError = ref('')

const columns: Column[] = [
  { key: 'no', label: '学号', width: '110px' },
  { key: 'name', label: '姓名', width: '100px' },
  { key: 'gender', label: '性别', width: '60px' },
  { key: 'grade', label: '年级', width: '70px' },
  { key: 'college', label: '学院' },
  { key: 'major', label: '专业' },
  { key: 'clazz', label: '班级', width: '120px' },
  { key: 'status', label: '学籍状态', width: '100px' },
  { key: 'act', label: '操作', width: '120px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    const res = await studentApi.page({
      page: page.value,
      size,
      keyword: filters.keyword || undefined,
      clazzId: filters.clazzId || undefined,
      status: filters.status || undefined,
    })
    rows.value = res.records
    total.value = res.total
    state.value = res.records.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '学生档案加载失败'
  }
}

onMounted(async () => {
  clazzes.value = await basicApi.clazzes().catch(() => [])
  await load()
})

function startCreate() {
  formError.value = ''
  editing.value = {
    id: null,
    studentNo: '',
    name: '',
    gender: '男',
    grade: 2022,
    collegeId: 1,
    majorId: 1,
    clazzId: 1,
    status: '在读',
    phone: '',
    email: '',
  }
}

async function save() {
  const f = editing.value
  if (!f) return
  if (!f.studentNo || !f.name) {
    formError.value = '学号与姓名不能为空'
    return
  }
  try {
    await studentApi.save(f)
    toast('已保存', 'ok')
    editing.value = null
    await load()
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : '保存失败'
  }
}

async function remove(s: StudentVO) {
  const ok = await confirmDialog({
    title: `删除 ${s.name}（${s.studentNo}）的档案？`,
    body: '有选课记录的学生不允许删除；如需终止学籍，应把学籍状态改为「退学」而不是删档案。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await studentApi.remove(s.id)
    toast('已删除', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '删除失败', 'bad')
  }
}
</script>

<template>
  <Plate title="学生档案" :note="`共 ${total} 人`">
    <template #actions>
      <Btn
        variant="quiet"
        @click="() => downloadCsv('/export/students', { keyword: filters.keyword }).then(() => toast('已导出当前筛选结果', 'ok')).catch((e) => toast(e.message ?? '导出失败', 'bad'))"
      >
        导出
      </Btn>
      <Btn variant="solid" @click="startCreate">新增学生</Btn>
    </template>

    <form v-if="editing" class="editor" @submit.prevent="save">
      <p class="editor__title">{{ editing.id ? '修改档案' : '新增档案' }}</p>
      <div class="editor__grid">
        <FieldRow label="学号"><input v-model="editing.studentNo" /></FieldRow>
        <FieldRow label="姓名"><input v-model="editing.name" /></FieldRow>
        <FieldRow label="性别">
          <select v-model="editing.gender"><option>男</option><option>女</option></select>
        </FieldRow>
        <FieldRow label="年级"><input v-model.number="editing.grade" type="number" class="num" /></FieldRow>
        <FieldRow label="班级">
          <select v-model.number="editing.clazzId">
            <option v-for="c in clazzes" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </FieldRow>
        <FieldRow label="学籍状态">
          <select v-model="editing.status">
            <option>在读</option><option>休学</option><option>退学</option><option>毕业</option>
          </select>
        </FieldRow>
        <FieldRow label="联系电话"><input v-model="editing.phone" class="num" /></FieldRow>
        <FieldRow label="电子邮箱"><input v-model="editing.email" class="num" /></FieldRow>
      </div>
      <p v-if="formError" class="editor__err">{{ formError }}</p>
      <div class="editor__act">
        <Btn variant="solid" type="submit">保存</Btn>
        <Btn @click="editing = null">取消</Btn>
      </div>
    </form>

    <form class="filters" @submit.prevent="((page = 1), load())">
      <FieldRow label="查找"><input v-model="filters.keyword" placeholder="学号或姓名" /></FieldRow>
      <FieldRow label="班级">
        <select v-model="filters.clazzId">
          <option value="">全部</option>
          <option v-for="c in clazzes" :key="c.id" :value="String(c.id)">{{ c.name }}</option>
        </select>
      </FieldRow>
      <FieldRow label="学籍状态">
        <select v-model="filters.status">
          <option value="">全部</option>
          <option>在读</option><option>休学</option><option>退学</option><option>毕业</option>
        </select>
      </FieldRow>
      <Btn variant="solid" type="submit">查询</Btn>
    </form>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="没有符合条件的学生"
      empty-detail="换个筛选条件，或者新增一条档案。"
      :skeleton-rows="6"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="980px">
        <tr v-for="s in rows" :key="s.id">
          <td><span class="num">{{ s.studentNo }}</span></td>
          <td>{{ s.name }}</td>
          <td>{{ s.gender }}</td>
          <td class="num">{{ s.grade }}</td>
          <td>{{ s.collegeName }}</td>
          <td>{{ s.majorName }}</td>
          <td>{{ s.clazzName }}</td>
          <td><StatusPlate :value="s.status" /></td>
          <td class="rowact">
            <Btn variant="quiet" @click="editing = { ...s }">修改</Btn>
            <Btn v-if="auth.isAdmin" variant="quiet" @click="remove(s)">删除</Btn>
          </td>
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
