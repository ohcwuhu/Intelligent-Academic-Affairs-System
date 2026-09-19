import type { Term } from '@/api/types'

const DAY = 86400000

/** 去掉时分秒，避免"今天"在跨零点时被算成前一天。 */
class Day extends Date {
  constructor(value: string | number | Date) {
    super(value)
    this.setHours(0, 0, 0, 0)
  }
}

export function weekdayText(weekday: number | null | undefined): string {
  const names = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
  return weekday && weekday >= 1 && weekday <= 7 ? names[weekday] : '时间待定'
}

/** 学期共几周，由起止日期算出。 */
export function termWeekCount(term: Term | null): number {
  if (!term?.startDate || !term?.endDate) return 20
  const days = (new Day(term.endDate).getTime() - new Day(term.startDate).getTime()) / DAY
  return Math.max(1, Math.ceil((days + 1) / 7))
}

/**
 * 当前教学周。
 *
 * <p>学期开始前或结束后一律返回 null：把假期算成"第 30 教学周"是错的，
 * 周次条只有十几格，对不上号会直接暴露成界面缺陷（这个缺陷实际发生过）。
 */
export function teachingWeek(term: Term | null, today = new Date()): number | null {
  if (!term?.startDate || !term?.endDate) return null
  const t = new Day(today).getTime()
  const start = new Day(term.startDate).getTime()
  const end = new Day(term.endDate).getTime()
  if (t < start || t > end) return null
  return Math.floor((t - start) / DAY / 7) + 1
}

export function scoreText(score: number | null): string {
  return score === null || score === undefined ? '未录入' : String(score)
}

export function creditText(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return Number(value).toFixed(1)
}

export function gpaText(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return Number(value).toFixed(2)
}
