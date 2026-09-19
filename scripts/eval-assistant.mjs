/**
 * 智能问答评测。
 *
 * 跑之前先起后端：cd backend && java -jar target/iaas-backend-1.0.0.jar
 * 然后：node scripts/eval-assistant.mjs
 *
 * 评测不看"回答读起来像不像"，只看可判定的四件事：
 *   1. 走的哪条路（意图与模式）——该拒答的有没有拒，该查库的有没有查库；
 *   2. 引用的哪一条——答毕业问题却只引补考条款，就是答错条款；
 *   3. 答案里该出现的数字在不在（1.5、15%、30分钟这类）；
 *   4. 数值接地——答案里的每个数字都要能在引用到的原文里找到。
 * 第 4 条是这个系统的核心承诺（数值不生成），所以做成硬性检查：
 * 拿答案里的数字去引用的切片正文里逐个找，找不到就判失败。
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const BASE = process.env.IAAS_API ?? 'http://127.0.0.1:8080'
const USER = process.env.IAAS_EVAL_USER ?? '2022001'
const PASSWORD = process.env.IAAS_EVAL_PASSWORD ?? '123456'
const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')

const suite = JSON.parse(readFileSync(join(ROOT, 'eval', 'assistant-cases.json'), 'utf8'))

async function login() {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASSWORD }),
  })
  const body = await res.json()
  if (body.code !== 0) throw new Error(`登录失败：${body.message}`)
  return body.data.token
}

const token = await login()
const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }

/** 取切片的完整正文，用于数值接地核对；摘要只有 300 字，不够。 */
const chunkCache = new Map()
async function chunkText(id) {
  if (chunkCache.has(id)) return chunkCache.get(id)
  const res = await fetch(`${BASE}/api/knowledge/chunks/${id}`, { headers })
  const body = await res.json()
  const text = body.code === 0 ? body.data.content ?? '' : ''
  chunkCache.set(id, text)
  return text
}

function digits(s) {
  return [...new Set(String(s).match(/\d+(?:\.\d+)?/g) ?? [])]
}

async function ask(question) {
  const res = await fetch(`${BASE}/api/assistant/ask`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ question }),
  })
  const body = await res.json()
  if (body.code !== 0) throw new Error(`问答接口失败：${body.message}`)
  return body.data
}

function judge(c, answer) {
  const problems = []
  const e = c.expect ?? {}
  const ans = answer.answer ?? ''
  const cites = answer.citations ?? []
  const cited = cites.map((x) => x.articleNo ?? '').join(' ') + ' ' +
    cites.map((x) => x.hierarchyPath ?? '').join(' ')

  if (e.intent && !e.intent.includes(answer.intent)) {
    problems.push(`意图 ${answer.intent} 不在期望 ${e.intent.join('/')}`)
  }
  if (e.mode && !e.mode.includes(answer.mode)) {
    problems.push(`模式 ${answer.mode} 不在期望 ${e.mode.join('/')}`)
  }
  if (e.citeAny && !e.citeAny.some((a) => cited.includes(a))) {
    problems.push(`没有引用 ${e.citeAny.join(' 或 ')}（实际引用：${cites.map((x) => x.articleNo).join('、') || '无'}）`)
  }
  if (e.answerAny && !e.answerAny.some((s) => ans.includes(s))) {
    problems.push(`答案里没有出现 ${e.answerAny.join(' / ')}`)
  }
  if (e.answerNone) {
    const bad = e.answerNone.find((s) => ans.includes(s))
    if (bad) problems.push(`答案里出现了不该有的说法：${bad}`)
  }
  if (e.citationsMin != null && cites.length < e.citationsMin) {
    problems.push(`引用数 ${cites.length} 少于 ${e.citationsMin}`)
  }
  if (e.citationsMax != null && cites.length > e.citationsMax) {
    problems.push(`引用数 ${cites.length} 多于 ${e.citationsMax}（拒答不该带引用）`)
  }
  if (e.dataKeys) {
    const keys = Object.keys(answer.data ?? {})
    const missing = e.dataKeys.filter((k) => !keys.includes(k))
    if (missing.length) problems.push(`返回数据缺字段 ${missing.join('、')}`)
  }
  return problems
}

/** 数值接地：答案里的数字必须在引用到的原文里出现。 */
async function groundingProblems(question, answer) {
  const cites = answer.citations ?? []
  if (!cites.length) return []
  const source = (await Promise.all(cites.map((c) => chunkText(c.chunkId)))).join('\n')
  const asked = new Set(digits(question))
  const ungrounded = digits(answer.answer).filter((d) => !source.includes(d) && !asked.has(d))
  return ungrounded.length ? [`答案里的数字在原文中找不到：${ungrounded.join('、')}`] : []
}

const rows = []
console.log(`\n智能问答评测：${suitesize(suite.cases)} 条，账号 ${USER}\n`)

function suitesize(cases) {
  return cases.length
}

for (const c of suite.cases) {
  const started = Date.now()
  let problems = []
  let detail = ''
  try {
    const answer = await ask(c.question)
    problems = judge(c, answer)
    const hardFail = problems.length > 0
    if (c.expect?.numberGrounding || !hardFail) {
      const g = await groundingProblems(c.question, answer)
      if (g.length) problems = problems.concat(g)
    }
    detail = `${answer.intent}/${answer.mode} ${answer.durationMs}ms 引用${(answer.citations ?? []).length}条`
    rows.push({
      id: c.id,
      layer: c.layer,
      question: c.question,
      intent: answer.intent,
      mode: answer.mode,
      citations: (answer.citations ?? []).map((x) => x.articleNo),
      durationMs: answer.durationMs,
      answer: answer.answer,
      notes: answer.notes,
      problems,
      ok: problems.length === 0,
    })
  } catch (err) {
    rows.push({ id: c.id, layer: c.layer, question: c.question, problems: [err.message], ok: false })
    problems = [err.message]
  }
  const flag = problems.length ? 'FAIL' : ' ok '
  console.log(`${flag} ${c.id} [${c.layer}] ${c.question}`)
  if (problems.length) problems.forEach((p) => console.log(`       ${p}`))
  else console.log(`       ${detail}`)
  void started
}

const failed = rows.filter((r) => !r.ok)
const byLayer = {}
for (const r of rows) {
  byLayer[r.layer] ??= { total: 0, failed: 0 }
  byLayer[r.layer].total++
  if (!r.ok) byLayer[r.layer].failed++
}

const timings = rows.filter((r) => r.durationMs).map((r) => r.durationMs).sort((a, b) => a - b)
const p50 = timings.length ? timings[Math.floor(timings.length / 2)] : 0
const p95 = timings.length ? timings[Math.min(timings.length - 1, Math.floor(timings.length * 0.95))] : 0
const citeCount = rows.filter((r) => r.ok && (r.citations ?? []).length).length

console.log('\n分层结果：')
for (const [layer, s] of Object.entries(byLayer)) {
  console.log(`  ${layer.padEnd(10, '　')} ${s.total - s.failed}/${s.total}`)
}
console.log(`\n通过 ${rows.length - failed.length}/${rows.length}，带引用的回答 ${citeCount} 条，` +
  `耗时 p50 ${p50}ms / p95 ${p95}ms`)

mkdirSync(join(ROOT, 'eval'), { recursive: true })
const report = {
  ranAt: new Date().toISOString(),
  base: BASE,
  user: USER,
  total: rows.length,
  passed: rows.length - failed.length,
  byLayer,
  latency: { p50, p95 },
  rows,
}
writeFileSync(join(ROOT, 'eval', 'report.json'), JSON.stringify(report, null, 2))
console.log(`明细写入 eval/report.json`)

process.exit(failed.length ? 1 : 0)
