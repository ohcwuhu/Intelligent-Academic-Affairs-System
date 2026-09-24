<script setup lang="ts">
/**
 * 申请审批（教务侧）。
 *
 * 待办排在前面，理由和系统预检结论并排显示：审批人要一眼看到
 * "学生为什么办"和"系统替他判了什么"。驳回必须写理由——
 * 学生看到驳回意见才知道下一步该干什么，这是流程里最容易漏掉的一环。
 */
import { onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { applicationApi } from '@/api'
import type { ApplicationRow } from '@/api/types'
import { toast } from '@/components/useToast'
import { promptDialog } from '@/components/useDialog'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'
import StatusPlate from '@/components/StatusPlate.vue'

const rows = ref<ApplicationRow[]>([])
const total = ref(0)
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const filter = ref<'待审' | '全部'>('待审')

const columns: Column[] = [
  { key: 'id', label: '编号', width: '60px', align: 'right' },
  { key: 'student', label: '学生', width: '140px' },
  { key: 'type', label: '事项', width: '130px' },
  { key: 'target', label: '对象' },
  { key: 'reason', label: '理由' },
  { key: 'precheck', label: '系统预检' },
  { key: 'status', label: '状态', width: '90px' },
  { key: 'act', label: '操作', width: '150px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    const res = await applicationApi.page({
      page: 1,
      size: 20,
      status: filter.value === '待审' ? '待审' : undefined,
    })
    rows.value = res.records
    total.value = res.total
    state.value = res.records.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '申请列表加载失败'
  }
}
onMounted(load)

async function review(row: ApplicationRow, action: 'APPROVE' | 'REJECT') {
  const note = await promptDialog({
    title:
      action === 'APPROVE'
        ? `通过「${row.studentName ?? ''}」的${row.typeText}`
        : `驳回「${row.studentName ?? ''}」的${row.typeText}`,
    body:
      action === 'APPROVE'
        ? `申请对象：${row.target}\n系统预检：${row.precheckNote ?? '无'}`
        : `申请对象：${row.target}\n驳回理由会直接展示给学生，请写清下一步该怎么办。`,
    label: action === 'APPROVE' ? '审批意见（可留空）' : '驳回理由',
    multiline: true,
    required: action === 'REJECT',
    requiredHint: '驳回必须写理由，学生据此才知道下一步怎么办',
    confirmText: action === 'APPROVE' ? '通过' : '驳回',
    danger: action === 'REJECT',
  })
  if (note === null) return
  try {
    await applicationApi.review(row.id, action, note ?? '')
    toast(action === 'APPROVE' ? '已通过' : '已驳回', action === 'APPROVE' ? 'ok' : 'info')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '审批失败', 'bad')
  }
}
</script>

<template>
  <Plate title="申请审批" :note="`共 ${total} 张单子，待办排在前面`">
    <template #actions>
      <Btn :variant="filter === '待审' ? 'solid' : 'quiet'" @click="filter = '待审'; load()">
        只看待审
      </Btn>
      <Btn :variant="filter === '全部' ? 'solid' : 'quiet'" @click="filter = '全部'; load()">
        全部
      </Btn>
    </template>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="没有待处理的申请"
      empty-detail="学生在「我的申请」里提交后，单子会出现在这里。"
      :skeleton-rows="5"
      @retry="load"
    >
      <DataTable :columns="columns" state="ready" min-width="1240px">
        <tr v-for="r in rows" :key="r.id">
          <td class="num num-end">{{ r.id }}</td>
          <td>
            <span class="num">{{ r.studentNo }}</span>
            <br />
            <span class="dim">{{ r.studentName }}</span>
          </td>
          <td>{{ r.typeText }}</td>
          <td>{{ r.target }}</td>
          <td class="dim">{{ r.reason }}</td>
          <td class="dim">{{ r.precheckNote ?? '—' }}</td>
          <td><StatusPlate :value="r.status" /></td>
          <td class="rowact">
            <template v-if="r.status === '待审'">
              <Btn variant="solid" @click="review(r, 'APPROVE')">通过</Btn>
              <Btn variant="quiet" @click="review(r, 'REJECT')">驳回</Btn>
            </template>
            <span v-else class="dim">
              {{ r.reviewer ?? '—' }}<template v-if="r.reviewNote">：{{ r.reviewNote }}</template>
            </span>
          </td>
        </tr>
      </DataTable>
    </StateHost>
  </Plate>
</template>
