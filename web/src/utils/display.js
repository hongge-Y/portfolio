/**
 * 首页要用到的展示型工具函数。
 * 与 format.ts 分开：那边是"和后端交换数据"的格式转换，这里是"给人看"的文案。
 */

/** 按时段问候：学生早上刷到首页的概率最高的是 6-11 点 */
export function greeting(date = new Date()) {
  const h = date.getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 13) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
}

const WEEK = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

export function weekdayLabel(date = new Date()) {
  return WEEK[date.getDay()]
}

/** 2026年10月30日 */
export function fullDateLabel(date = new Date()) {
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

/** 10/30（列表右侧的短日期） */
export function shortDate(v) {
  if (!v) return ''
  const s = v.replace('T', ' ').slice(0, 10)
  const parts = s.split('-')
  return parts.length === 3 ? `${parts[1]}/${parts[2]}` : s
}

/** "2026-10-30 14:20:00" → "今天 14:20" / "昨天" / "10/28" */
export function friendlyTime(v) {
  if (!v) return ''
  const t = new Date(v.replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return shortDate(v)
  const now = Date.now()
  const day = 86400000
  const diff = now - t
  if (diff < day && new Date(t).getDate() === new Date().getDate()) {
    return `今天 ${v.slice(11, 16)}`
  }
  if (diff < 2 * day) return '昨天'
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return shortDate(v)
}

/** 距离开课还有几天，用于赛事/报名截止 */
export function daysLeft(v) {
  if (!v) return null
  const t = new Date(v.replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return null
  return Math.ceil((t - Date.now()) / 86400000)
}

/** 数组按 key 分组（课表按星期分组用） */
export function groupBy(list, key) {
  return list.reduce((acc, item) => {
    const k = String(key(item))
    ;(acc[k] ||= []).push(item)
    return acc
  }, {})
}
