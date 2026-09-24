<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '@/api/client'
import { userApi } from '@/api'
import type { AppUser } from '@/api/types'
import { toast } from '@/components/useToast'
import { promptDialog } from '@/components/useDialog'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const rows = ref<AppUser[]>([])
const total = ref(0)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const creating = ref(false)
const form = reactive({ username: '', password: '', realName: '', role: 'STUDENT', refId: '' })
const formError = ref('')

const roleName: Record<string, string> = {
  ADMIN: '系统管理员',
  ACADEMIC: '教务管理员',
  TEACHER: '教师',
  STUDENT: '学生',
}

const columns: Column[] = [
  { key: 'username', label: '登录名', width: '120px' },
  { key: 'name', label: '姓名', width: '110px' },
  { key: 'role', label: '角色', width: '120px' },
  { key: 'ref', label: '关联业务主键', width: '120px', align: 'right' },
  { key: 'last', label: '最近登录', width: '180px' },
  { key: 'status', label: '状态', width: '80px' },
  { key: 'act', label: '操作', width: '190px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    const res = await userApi.page({ page: 1, size: 50 })
    rows.value = res.records
    total.value = res.total
    state.value = res.records.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '账号列表加载失败'
  }
}
onMounted(load)

async function create() {
  formError.value = ''
  if (!form.username || form.password.length < 6) {
    formError.value = '登录名不能为空，口令至少 6 位'
    return
  }
  try {
    await userApi.create({
      username: form.username,
      password: form.password,
      realName: form.realName || form.username,
      role: form.role,
      refId: form.refId ? Number(form.refId) : null,
    })
    toast('账号已创建', 'ok')
    creating.value = false
    form.username = ''
    form.password = ''
    form.realName = ''
    form.refId = ''
    await load()
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : '创建失败'
  }
}

async function resetPassword(u: AppUser) {
  const pwd = await promptDialog({
    title: `为 ${u.realName} 重置口令`,
    body: '口令由管理员设置并当面告知本人；系统不存明文，忘记后只能再重置。',
    label: '新口令（至少 6 位）',
    required: true,
    requiredHint: '口令不能为空',
  })
  if (pwd === null) return
  if (pwd.length < 6) {
    toast('口令至少 6 位', 'bad')
    return
  }
  try {
    await userApi.resetPassword(u.id, pwd)
    toast('口令已重置', 'ok')
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '重置失败', 'bad')
  }
}

async function toggle(u: AppUser) {
  const next = u.status === 1 ? 0 : 1
  try {
    await userApi.setStatus(u.id, next)
    toast(next === 1 ? '账号已启用' : '账号已停用', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '操作失败', 'bad')
  }
}
</script>

<template>
  <Plate title="账号管理" :note="`共 ${total} 个账号`">
    <template #actions>
      <Btn variant="solid" @click="creating = !creating">新建账号</Btn>
    </template>

    <form v-if="creating" class="editor" @submit.prevent="create">
      <div class="editor__grid">
        <FieldRow label="登录名" hint="学号或工号"><input v-model="form.username" class="num" /></FieldRow>
        <FieldRow label="初始口令" hint="至少 6 位"><input v-model="form.password" class="num" /></FieldRow>
        <FieldRow label="姓名"><input v-model="form.realName" /></FieldRow>
        <FieldRow label="角色">
          <select v-model="form.role">
            <option value="STUDENT">学生</option>
            <option value="TEACHER">教师</option>
            <option value="ACADEMIC">教务管理员</option>
            <option value="ADMIN">系统管理员</option>
          </select>
        </FieldRow>
        <FieldRow label="关联主键" hint="教师填教师ID，学生填学生ID">
          <input v-model="form.refId" class="num" />
        </FieldRow>
      </div>
      <p v-if="formError" class="editor__err">{{ formError }}</p>
      <div class="editor__act">
        <Btn variant="solid" type="submit">创建</Btn>
        <Btn @click="creating = false">取消</Btn>
      </div>
    </form>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="还没有账号"
      empty-detail="先创建一个账号，再把它分配给师生。"
      :skeleton-rows="5"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="900px">
        <tr v-for="u in rows" :key="u.id">
          <td><span class="num">{{ u.username }}</span></td>
          <td>{{ u.realName }}</td>
          <td>{{ roleName[u.role] ?? u.role }}</td>
          <td class="num num-end">{{ u.refId ?? '-' }}</td>
          <td class="num">{{ u.lastLogin ?? '从未登录' }}</td>
          <td><StatusPlate :value="u.status === 1 ? '启用' : '停用'" /></td>
          <td class="rowact">
            <Btn variant="quiet" @click="resetPassword(u)">重置口令</Btn>
            <Btn variant="quiet" @click="toggle(u)">{{ u.status === 1 ? '停用' : '启用' }}</Btn>
          </td>
        </tr>
      </DataTable>
    </StateHost>
  </Plate>
</template>
