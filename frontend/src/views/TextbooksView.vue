<script setup lang="ts">
/**
 * 教材订购。
 *
 * 学生看到的是"本学期我选的课用什么教材"，点一下订、再点一下取消。
 * 教材不是学校统一发的，所以这里只记录订购意向与金额小计，
 * 真正的领书与结算仍在线下进行——页面上写清楚，别让人以为点了就扣钱。
 */
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { textbookApi } from '@/api'
import type { MyTextbooks } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import DataTable from '@/components/DataTable.vue'
import type { Column } from '@/components/DataTable.vue'
import StateHost from '@/components/StateHost.vue'

const auth = useAuthStore()
const mine = ref<MyTextbooks | null>(null)
const staffRows = ref<Record<string, unknown>[]>([])
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const busyId = ref<number | null>(null)

const columns: Column[] = [
  { key: 'course', label: '课程', width: '200px' },
  { key: 'title', label: '教材' },
  { key: 'publisher', label: '出版社', width: '150px' },
  { key: 'isbn', label: 'ISBN', width: '140px' },
  { key: 'price', label: '定价(元)', width: '90px', align: 'right' },
  { key: 'act', label: '订购', width: '100px', align: 'right' },
]

const staffColumns: Column[] = [
  { key: 'class', label: '教学班', width: '200px' },
  { key: 'title', label: '教材' },
  { key: 'publisher', label: '出版社', width: '150px' },
  { key: 'price', label: '定价(元)', width: '90px', align: 'right' },
  { key: 'count', label: '已订购人数', width: '110px', align: 'right' },
]

async function load() {
  state.value = 'loading'
  try {
    if (auth.isStudent) {
      mine.value = await textbookApi.mine()
      state.value = mine.value.rows.length ? 'ready' : 'empty'
    } else {
      staffRows.value = await textbookApi.list()
      state.value = staffRows.value.length ? 'ready' : 'empty'
    }
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '教材信息加载失败'
  }
}
onMounted(load)

async function toggle(rowId: number, ordered: boolean) {
  busyId.value = rowId
  try {
    if (ordered) {
      await textbookApi.cancel(rowId)
      toast('已取消订购', 'info')
    } else {
      await textbookApi.order(rowId)
      toast('已订购，领书时间以教务处通知为准', 'ok')
    }
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '操作失败', 'bad')
  } finally {
    busyId.value = null
  }
}

const orderedCount = computed(() => mine.value?.orderedCount ?? 0)
</script>

<template>
  <Plate
    title="教材订购"
    :note="auth.isStudent
      ? `已订 ${orderedCount} 本，合计 ${mine?.orderedAmount ?? 0} 元（全书合计 ${mine?.totalAmount ?? 0} 元）`
      : '教材挂在教学班上；这里看的是各班的教材与订购人数'"
  >
    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="本学期还没有教材信息"
      empty-detail="教务录入教材后会出现在这里。"
      :skeleton-rows="4"
      @retry="load"
    >
      <DataTable v-if="auth.isStudent" :columns="columns" state="ready" min-width="980px">
        <tr v-for="r in mine?.rows ?? []" :key="r.textbookId">
          <td>
            {{ r.courseName }} <span class="dim">（{{ r.courseCode }}）</span>
          </td>
          <td>
            {{ r.title }}
            <span v-if="r.author" class="dim">／{{ r.author }}</span>
          </td>
          <td class="dim">{{ r.publisher ?? '—' }}</td>
          <td class="num dim">{{ r.isbn ?? '—' }}</td>
          <td class="num num-end">{{ r.price ?? '—' }}</td>
          <td class="rowact">
            <Btn
              :variant="r.ordered ? 'quiet' : 'solid'"
              :loading="busyId === r.textbookId"
              @click="toggle(r.textbookId, r.ordered)"
            >
              {{ r.ordered ? '取消订购' : '订购' }}
            </Btn>
          </td>
        </tr>
      </DataTable>

      <DataTable v-else :columns="staffColumns" state="ready" min-width="900px">
        <tr v-for="r in staffRows" :key="String(r.id)">
          <td class="num">{{ r.teachingClassCode }}</td>
          <td>{{ r.title }}</td>
          <td class="dim">{{ r.publisher ?? '—' }}</td>
          <td class="num num-end">{{ r.price ?? '—' }}</td>
          <td class="num num-end">{{ r.orderedCount }}</td>
        </tr>
      </DataTable>
    </StateHost>
    <p class="hint">
      订购只是登记意向，教材领取与结算仍由教务处统一安排；实际价格以出版社定价与学校通知为准。
    </p>
  </Plate>
</template>

<style scoped>
.hint {
  padding: var(--s-3) var(--s-4) var(--s-4);
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.dim {
  color: var(--ink-muted);
  font-size: var(--t-xs);
}
</style>
