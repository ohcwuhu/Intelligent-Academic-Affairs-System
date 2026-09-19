<script setup lang="ts">
/**
 * 智能问答。
 *
 * 界面不美化能力边界：每条答复都挂着方式标牌，如实说明这一条是模型生成的、
 * 是原文摘录、还是你自己在教务系统里的数据。有依据就列出依据，
 * 没依据就明说没依据，不用模糊措辞掩盖。
 */
import { computed, onMounted, ref } from 'vue'
import { ApiError } from '@/api/client'
import { assistantApi, feedbackApi, knowledgeApi } from '@/api'
import type {
  AssistantAnswer,
  AssistantStatus,
  ChatConversation,
  ChatMessage,
  Citation,
  KnowledgeChunkDetail,
} from '@/api/types'
import Plate from '@/components/Plate.vue'
import Btn from '@/components/Btn.vue'
import StatusPlate from '@/components/StatusPlate.vue'

interface Turn {
  no: number
  question: string
  answer: AssistantAnswer
  feedback: '' | 'USEFUL' | 'USELESS' | 'WRONG'
  /** 从会话记录里读回来的历史轮次：只读，不给反馈按钮（当时没评就不补评）。 */
  history?: boolean
}

const status = ref<AssistantStatus | null>(null)
const stats = ref<{ documents: number; chunks: number } | null>(null)
const question = ref('')
const turns = ref<Turn[]>([])
const asking = ref(false)
const error = ref('')
const openedChunk = ref<KnowledgeChunkDetail | null>(null)
const conversationId = ref<number | null>(null)
const conversations = ref<ChatConversation[]>([])
const historyState = ref<'loading' | 'ready' | 'empty' | 'error'>('loading')
let seq = 0

const examples = [
  '缓考能申请几门',
  '重修要交钱吗',
  '转专业有什么条件',
  '考试作弊怎么处理',
  '休学以后怎么复学',
  '我的学分够不够毕业',
  '我要办休学需要哪些材料',
  '帮我查一下我室友的成绩',
]

const modeText: Record<string, string> = {
  generated: '模型依据原文生成',
  extractive: '原文摘录',
  process: '办理指引',
  tool: '你的教务数据',
  refusal: '无法回答',
  clarify: '需要补充信息',
}
const modeTone: Record<string, string> = {
  generated: '开放',
  extractive: '结课',
  process: '在职',
  tool: '在读',
  refusal: '停开',
  clarify: '其他',
}

const llmHint = computed(() => {
  const s = status.value
  if (!s) return ''
  return s.llmReady
    ? `已接入 ${s.model}，回答由模型依据原文生成，数字逐条与原文核对`
    : '当前未接入生成模型，回答直接给出知识库原文，检索与引用链路完整'
})

async function load() {
  try {
    const [s, k] = await Promise.all([
      assistantApi.status(),
      knowledgeApi.stats().catch(() => null),
    ])
    status.value = s
    stats.value = k
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '问答服务不可用'
  }
}

/**
 * 会话记录。多轮上下文存在服务端，前端只存一个 conversationId；
 * 换个设备打开也能接着上次的对话问，所以历史要能读回来。
 */
async function loadConversations() {
  historyState.value = 'loading'
  try {
    conversations.value = await assistantApi.conversations()
    historyState.value = conversations.value.length ? 'ready' : 'empty'
  } catch {
    historyState.value = 'error'
  }
}

onMounted(() => {
  void load()
  void loadConversations()
})

/** 把会话里的消息按「问—答」配成轮次，复用当前的消息展示。 */
function toTurns(messages: ChatMessage[]): Turn[] {
  const built: Turn[] = []
  let pending: Turn | null = null
  for (const m of messages) {
    if (m.role === 'user') {
      pending = {
        no: ++seq,
        question: m.content,
        feedback: '',
        history: true,
        answer: {
          intent: (m.intent ?? 'RULE') as AssistantAnswer['intent'],
          mode: (m.mode ?? 'extractive') as AssistantAnswer['mode'],
          answer: '',
          citations: [],
          notes: [],
          data: null,
          conversationId: m.conversationId,
          durationMs: 0,
        },
      }
      built.push(pending)
    } else if (pending) {
      pending.answer = { ...pending.answer, answer: m.content }
      pending = null
    }
  }
  return built
}

async function openConversation(c: ChatConversation) {
  error.value = ''
  try {
    const messages = await assistantApi.conversationMessages(c.id)
    turns.value = toTurns(messages).reverse()
    conversationId.value = c.id
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '会话读取失败'
  }
}

/** 开一段新对话：换掉会话 id，模型这边的上下文也就断了。 */
function newConversation() {
  conversationId.value = null
  turns.value = []
  error.value = ''
}

async function ask(text?: string) {
  const q = (text ?? question.value).trim()
  if (!q) {
    error.value = '请先输入要问的问题'
    return
  }
  error.value = ''
  asking.value = true
  try {
    const answer = await assistantApi.ask(q, conversationId.value)
    conversationId.value = answer.conversationId
    turns.value.unshift({ no: ++seq, question: q, answer, feedback: '' })
    question.value = ''
    void loadConversations()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '提问失败'
  } finally {
    asking.value = false
  }
}

async function openCitation(c: Citation) {
  try {
    openedChunk.value = await knowledgeApi.chunk(c.chunkId)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '原文加载失败'
  }
}

async function sendFeedback(t: Turn, type: 'USEFUL' | 'USELESS' | 'WRONG') {
  let detail = ''
  if (type === 'WRONG') {
    detail = prompt('请说明答案错在哪里，便于教务处核对') ?? ''
    if (!detail.trim()) return
  }
  try {
    await feedbackApi.submit({
      question: t.question,
      type,
      detail,
      answerMode: t.answer.mode,
      answerDigest: t.answer.answer.slice(0, 400),
      citationPath: t.answer.citations[0]?.hierarchyPath,
    })
    t.feedback = type
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '反馈提交失败'
  }
}
</script>

<template>
  <Plate title="智能问答" :note="llmHint">
    <template #actions>
      <Btn @click="newConversation">开始新对话</Btn>
      <span v-if="conversationId" class="ask__conv num">会话 #{{ conversationId }}</span>
    </template>

    <div class="ask">
      <form class="ask__form" @submit.prevent="ask()">
        <label class="sr" for="q">要问的问题</label>
        <input id="q" v-model="question" class="ask__input" placeholder="例如：缓考能申请几门" maxlength="200" />
        <Btn variant="solid" type="submit" :loading="asking">{{ asking ? '检索中' : '提问' }}</Btn>
      </form>

      <div class="ask__examples">
        <span class="ask__examples-label">示例</span>
        <button v-for="e in examples" :key="e" type="button" class="ask__example" @click="ask(e)">
          {{ e }}
        </button>
      </div>

      <p v-if="error" class="ask__error" role="alert">{{ error }}</p>

      <dl v-if="stats" class="ask__stats">
        <div><dt>知识库文档</dt><dd class="num">{{ stats.documents }}</dd></div>
        <div><dt>原文切片</dt><dd class="num">{{ stats.chunks }}</dd></div>
        <div><dt>检索方式</dt><dd>关键词 ngram 全文</dd></div>
        <div><dt>生成方式</dt><dd>{{ status?.llmReady ? status.model : '未接入，用原文摘录' }}</dd></div>
      </dl>
    </div>

    <div v-if="!turns.length" class="hint">
      <p class="hint__title">还什么都没问</p>
      <p class="hint__body">
        上面点一个示例问题，或者自己输入。回答会附上依据的原文条款，点开卡片能看到完整原文。
        数字类的问题（比如你差多少学分）走的是教务系统里的实时数据，不经过模型推算。
      </p>
    </div>

    <ol v-else class="slips">
      <li v-for="t in turns" :key="t.no" class="slip">
        <header class="slip__head">
          <span class="slip__no num">{{ String(t.no).padStart(2, '0') }}</span>
          <p class="slip__q">{{ t.question }}</p>
          <StatusPlate :value="modeTone[t.answer.mode] ?? '其他'" />
          <span class="slip__mode">{{ modeText[t.answer.mode] ?? t.answer.mode }}</span>
          <span class="slip__ms num">{{ t.answer.durationMs }}ms</span>
        </header>

        <p class="slip__a">{{ t.answer.answer }}</p>

        <section v-if="t.answer.citations.length" class="cites">
          <p class="cites__title">依据（{{ t.answer.citations.length }} 条）</p>
          <ul>
            <li v-for="c in t.answer.citations" :key="c.chunkId">
              <button type="button" class="cite" @click="openCitation(c)">
                <span class="cite__path">{{ c.hierarchyPath }}</span>
                <span class="cite__excerpt">{{ c.excerpt }}</span>
                <span class="cite__meta">
                  {{ c.documentTitle }} · {{ c.dept }}
                  <template v-if="c.effectiveDate"> · 生效 {{ c.effectiveDate }}</template>
                </span>
              </button>
            </li>
          </ul>
        </section>

        <ul v-if="t.answer.notes.length" class="notes">
          <li v-for="(n, i) in t.answer.notes" :key="i">{{ n }}</li>
        </ul>

        <div class="acts">
          <span class="acts__label">这条回答：</span>
          <Btn variant="quiet" :disabled="!!t.feedback" @click="sendFeedback(t, 'USEFUL')">有用</Btn>
          <Btn variant="quiet" :disabled="!!t.feedback" @click="sendFeedback(t, 'USELESS')">没用</Btn>
          <Btn variant="quiet" :disabled="!!t.feedback" @click="sendFeedback(t, 'WRONG')">内容有误</Btn>
          <span v-if="t.feedback" class="acts__done">已记录，教务处会看到</span>
        </div>
      </li>
    </ol>
  </Plate>

  <Plate title="历史对话" note="多轮上下文存在服务端；点开一段可以接着问">
    <div v-if="historyState === 'ready'" class="conv">
      <button
        v-for="c in conversations"
        :key="c.id"
        type="button"
        class="conv__item"
        :class="{ 'is-here': c.id === conversationId }"
        @click="openConversation(c)"
      >
        <span class="conv__title">{{ c.title || '(无标题)' }}</span>
        <span class="conv__meta">
          <span class="num">{{ c.turnCount }}</span> 轮 · {{ (c.updatedAt ?? '').replace('T', ' ').slice(5, 16) }}
        </span>
      </button>
    </div>
    <p v-else-if="historyState === 'loading'" class="conv__none">正在读取会话记录</p>
    <p v-else-if="historyState === 'empty'" class="conv__none">
      还没有历史对话。问过的问题会存成会话，换设备也能接着问。
    </p>
    <p v-else class="conv__none">会话记录暂时读不到，问答本身不受影响。</p>
  </Plate>

  <div v-if="openedChunk" class="drawer" role="dialog" aria-modal="true">
    <div class="drawer__panel">
      <header class="drawer__head">
        <p class="drawer__path">{{ openedChunk.hierarchyPath }}</p>
        <Btn variant="quiet" @click="openedChunk = null">关闭</Btn>
      </header>
      <p class="drawer__body">{{ openedChunk.content }}</p>
      <p class="drawer__foot">以上为知识库中的原文，逐字照录，未经改写。</p>
    </div>
  </div>
</template>

<style scoped>
.ask__conv {
  margin-left: var(--s-3);
  font-size: var(--t-xs);
  color: var(--ink-muted);
}

.conv {
  display: grid;
}
.conv__item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--s-4);
  padding: var(--s-3) var(--s-4);
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--line);
  text-align: left;
  cursor: pointer;
  font: inherit;
  color: inherit;
}
.conv__item:last-child {
  border-bottom: 0;
}
.conv__item:hover {
  background: var(--ground);
}
.conv__item.is-here {
  box-shadow: inset 3px 0 0 var(--accent);
}
.conv__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv__meta {
  flex: none;
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.conv__none {
  padding: var(--s-4);
  color: var(--ink-muted);
  font-size: var(--t-sm);
}

.ask {
  padding: var(--s-4);
  border-bottom: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: var(--s-3);
}
.ask__form {
  display: flex;
  gap: var(--s-2);
}
.ask__input {
  flex: 1;
  height: 34px;
  padding: 0 var(--s-3);
  background: var(--face);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  font-size: var(--t-base);
}
.ask__input:focus {
  border-color: var(--accent-deep);
}
.ask__examples {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  flex-wrap: wrap;
}
.ask__examples-label {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.ask__example {
  background: transparent;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 2px var(--s-2);
  font-size: var(--t-xs);
  color: var(--accent-deep);
  cursor: pointer;
}
.ask__example:hover {
  border-color: var(--accent);
}
.ask__error {
  font-size: var(--t-sm);
  color: var(--bad);
}
.ask__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
  border-top: 1px solid var(--line);
}
.ask__stats > div {
  padding: var(--s-2) var(--s-3) 0;
  border-right: 1px solid var(--line);
}
.ask__stats > div:last-child {
  border-right: 0;
}
.ask__stats dt {
  font-size: 11px;
  color: var(--ink-muted);
}
.ask__stats dd {
  margin: 2px 0 0;
  font-size: var(--t-sm);
}
.hint {
  padding: var(--s-8) var(--s-4);
  text-align: center;
}
.hint__title {
  font-size: var(--t-base);
  font-weight: 600;
}
.hint__body {
  margin-top: var(--s-2);
  font-size: var(--t-sm);
  color: var(--ink-muted);
  max-width: 56ch;
  margin-inline: auto;
}
.slips {
  list-style: none;
  margin: 0;
  padding: 0;
}
.slip {
  padding: var(--s-4);
  border-bottom: 1px solid var(--line);
}
.slip:last-child {
  border-bottom: 0;
}
.slip__head {
  display: flex;
  align-items: baseline;
  gap: var(--s-3);
  flex-wrap: wrap;
}
.slip__no {
  font-size: var(--t-sm);
  color: var(--ink-muted);
}
.slip__q {
  font-size: var(--t-base);
  font-weight: 600;
  flex: 1;
  min-width: 200px;
}
.slip__mode,
.slip__ms {
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.slip__a {
  margin-top: var(--s-3);
  font-size: var(--t-sm);
  line-height: 1.75;
  white-space: pre-line;
  max-width: 74ch;
}
.cites {
  margin-top: var(--s-4);
  border-top: 1px solid var(--line);
  padding-top: var(--s-3);
}
.cites__title {
  font-size: var(--t-xs);
  color: var(--ink-muted);
  margin-bottom: var(--s-2);
}
.cites ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--s-2);
}
.cite {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  text-align: left;
  background: var(--face);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: var(--s-2) var(--s-3);
  cursor: pointer;
}
.cite:hover {
  border-color: var(--line-strong);
}
.cite__path {
  font-size: var(--t-xs);
  color: var(--accent-deep);
}
.cite__excerpt {
  font-size: var(--t-xs);
  color: var(--ink-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.cite__meta {
  font-size: 11px;
  color: var(--ink-muted);
}
.notes {
  margin: var(--s-3) 0 0;
  padding-left: 1.1em;
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
.acts {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  margin-top: var(--s-3);
  padding-top: var(--s-3);
  border-top: 1px solid var(--line);
  font-size: var(--t-xs);
}
.acts__label {
  color: var(--ink-muted);
}
.acts__done {
  color: var(--ok);
}
.drawer {
  position: fixed;
  inset: 0;
  background: rgb(19 22 25 / 0.55);
  display: flex;
  justify-content: flex-end;
  z-index: var(--z-overlay);
}
.drawer__panel {
  width: min(560px, 92vw);
  background: var(--face-raised);
  border-left: 1px solid var(--line-strong);
  display: flex;
  flex-direction: column;
  padding: var(--s-4);
  gap: var(--s-3);
  overflow-y: auto;
}
.drawer__head {
  display: flex;
  align-items: baseline;
  gap: var(--s-3);
  border-bottom: 1px solid var(--line);
  padding-bottom: var(--s-2);
}
.drawer__path {
  flex: 1;
  font-size: var(--t-sm);
  font-weight: 600;
}
.drawer__body {
  font-size: var(--t-sm);
  line-height: 1.85;
  white-space: pre-line;
}
.drawer__foot {
  margin-top: auto;
  padding-top: var(--s-3);
  border-top: 1px solid var(--line);
  font-size: var(--t-xs);
  color: var(--ink-muted);
}
@media (max-width: 900px) {
  .ask__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .ask__stats > div:nth-child(2) {
    border-right: 0;
  }
}
</style>
