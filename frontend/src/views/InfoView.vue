<script setup lang="ts">
/**
 * 通知与留言。
 *
 * 通知是"教务发、大家看"，留言是"学生问、教务答"，两类信息放一页但分区清楚：
 * 通知只读（教务可发布），留言学生只能看自己的。
 */
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { infoApi } from '@/api'
import type { MessageRow, NoticeRow } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/components/useToast'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import FieldRow from '@/components/FieldRow.vue'
import StateHost from '@/components/StateHost.vue'

const auth = useAuthStore()
const notices = ref<NoticeRow[]>([])
const messages = ref<MessageRow[]>([])
const state = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
const errorDetail = ref('')
const composing = ref(false)
const draft = ref({ title: '', content: '', pinned: false })
const asking = ref('')

async function load() {
  state.value = 'loading'
  try {
    notices.value = await infoApi.notices()
    if (!auth.isTeacher) {
      messages.value = await infoApi.messages()
    }
    state.value = notices.value.length ? 'ready' : 'empty'
  } catch (e) {
    state.value = 'error'
    errorDetail.value = e instanceof ApiError ? e.message : '通知加载失败'
  }
}
onMounted(load)

async function publish() {
  if (!draft.value.title.trim() || !draft.value.content.trim()) {
    toast('标题与正文都要填', 'bad')
    return
  }
  try {
    await infoApi.publish({ ...draft.value, targetRole: null })
    toast('已发布', 'ok')
    draft.value = { title: '', content: '', pinned: false }
    composing.value = false
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '发布失败', 'bad')
  }
}

async function ask() {
  if (!asking.value.trim()) {
    toast('留言内容不能为空', 'bad')
    return
  }
  try {
    await infoApi.ask(asking.value)
    toast('已提交，教务处会在这里回复', 'ok')
    asking.value = ''
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '提交失败', 'bad')
  }
}

async function reply(row: MessageRow) {
  const text = prompt('回复内容（学生会在同一页看到）', row.reply ?? '')
  if (text === null || !text.trim()) return
  try {
    await infoApi.reply(row.id, text)
    toast('已回复', 'ok')
    await load()
  } catch (e) {
    toast(e instanceof ApiError ? e.message : '回复失败', 'bad')
  }
}

const pendingCount = computed(() => messages.value.filter((m) => !m.reply).length)
</script>

<template>
  <Plate title="通知与留言" :note="auth.isStudent ? '教务通知在这里，遇到问题可以直接留言' : '发布通知、回复学生留言'">
    <template #actions>
      <Btn v-if="!auth.isStudent" variant="solid" @click="composing = !composing">
        {{ composing ? '取消' : '发布通知' }}
      </Btn>
    </template>

    <div v-if="composing" class="form">
      <FieldRow label="标题" for-id="ntitle">
        <input id="ntitle" v-model="draft.title" maxlength="120" />
      </FieldRow>
      <FieldRow label="正文" for-id="ncontent">
        <textarea id="ncontent" v-model="draft.content" rows="3" />
      </FieldRow>
      <label class="check">
        <input v-model="draft.pinned" type="checkbox" /> 置顶
      </label>
      <div class="form__act">
        <Btn variant="solid" @click="publish">发布</Btn>
      </div>
    </div>

    <StateHost
      :state="state"
      :error-detail="errorDetail"
      empty-title="还没有通知"
      empty-detail="教务发布通知后会出现在这里。"
      :skeleton-rows="4"
      @retry="load"
    >
      <ul class="notices">
        <li v-for="n in notices" :key="n.id" class="notice" :class="{ 'is-pinned': n.pinned }">
          <p class="notice__head">
            <span v-if="n.pinned" class="tag">置顶</span>
            <span class="notice__title">{{ n.title }}</span>
            <span class="notice__meta">{{ n.publisher }}　{{ n.publishedAt }}</span>
          </p>
          <p class="notice__body">{{ n.content }}</p>
        </li>
      </ul>
    </StateHost>
  </Plate>

  <Plate
    v-if="!auth.isTeacher"
    title="我的留言"
    :note="auth.isStudent ? '问教务处的事写在这里，回复会出现在同一条下面' : `待回复 ${pendingCount} 条`"
  >
    <div v-if="auth.isStudent" class="form form--row">
      <FieldRow label="留言" for-id="ask">
        <input id="ask" v-model="asking" maxlength="1000" placeholder="例如：我的重修缴费在哪里交？" />
      </FieldRow>
      <Btn variant="solid" @click="ask">提交</Btn>
    </div>

    <ul class="messages">
      <li v-for="m in messages" :key="m.id" class="message">
        <p class="message__head">
          <span class="num">{{ m.studentNo }}</span> {{ m.studentName }}
          <span class="message__meta">{{ m.createdAt }}</span>
        </p>
        <p class="message__body">{{ m.content }}</p>
        <div v-if="m.reply" class="message__reply">
          <span class="tag tag--reply">教务处回复</span>
          {{ m.reply }}
          <span class="message__meta">{{ m.repliedBy }}　{{ m.repliedAt }}</span>
        </div>
        <div v-else class="message__act">
          <span class="dim">还没有回复</span>
          <Btn v-if="!auth.isStudent" variant="quiet" @click="reply(m)">回复</Btn>
        </div>
      </li>
      <li v-if="!messages.length" class="none">还没有留言。</li>
    </ul>
  </Plate>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--s-3);
  padding: var(--s-4);
  border-bottom: 1px solid var(--line);
  max-width: 720px;
}
.form--row {
  flex-direction: row;
  align-items: flex-end;
  gap: var(--s-3);
}
.form--row :deep(.field) {
  flex: 1;
}
.form__act {
  display: flex;
  justify-content: flex-end;
}
.check {
  font-size: var(--t-sm);
}
.notices,
.messages {
  list-style: none;
  margin: 0;
  padding: 0;
}
.notice {
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
}
.notice.is-pinned {
  background: var(--face);
  box-shadow: inset 3px 0 0 var(--accent);
}
.notice__head {
  display: flex;
  align-items: baseline;
  gap: var(--s-3);
}
.notice__title {
  font-weight: 600;
}
.notice__meta,
.message__meta {
  font-size: var(--t-xs);
  color: var(--ink-muted);
  margin-left: auto;
}
.notice__body {
  margin-top: var(--s-2);
  font-size: var(--t-sm);
  color: var(--ink-muted);
  white-space: pre-wrap;
}
.tag {
  padding: 0 6px;
  background: var(--accent);
  color: var(--face);
  font-size: var(--t-xs);
}
.tag--reply {
  background: var(--structure);
  margin-right: var(--s-2);
}
.message {
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--line);
}
.message__head {
  font-size: var(--t-sm);
}
.message__body {
  margin-top: var(--s-1);
  font-size: var(--t-sm);
  white-space: pre-wrap;
}
.message__reply {
  margin-top: var(--s-2);
  padding: var(--s-2) var(--s-3);
  background: var(--face);
  font-size: var(--t-sm);
  border-left: 3px solid var(--accent);
}
.message__act {
  margin-top: var(--s-2);
  display: flex;
  align-items: center;
  gap: var(--s-3);
}
.none {
  padding: var(--s-4);
  color: var(--ink-muted);
  font-size: var(--t-sm);
}
.dim {
  color: var(--ink-muted);
  font-size: var(--t-xs);
}
</style>
