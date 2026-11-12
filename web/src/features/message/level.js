/**
 * 站内信等级 —— 文案与配色的唯一出处。
 * api/message.js 里的 levelText/levelClass 仍然导出，内部转到这里，老页面不用改。
 */

export const LEVEL_TEXT = ['普通', '紧急', '警告', '致命']

const LEVEL_CLASS = {
  3: 'bg-rose-100 text-rose-700',
  2: 'bg-orange-100 text-orange-700',
  1: 'bg-amber-100 text-amber-700',
  0: 'bg-slate-100 text-slate-500'
}

/** 未读左侧色条（列表视觉锚点，比只加粗更容易扫） */
const LEVEL_BAR = {
  3: 'bg-rose-500',
  2: 'bg-orange-400',
  1: 'bg-amber-400',
  0: 'bg-slate-300'
}

export function levelText(level) {
  return LEVEL_TEXT[level] ?? LEVEL_TEXT[0]
}

export function levelClass(level) {
  return LEVEL_CLASS[level] ?? LEVEL_CLASS[0]
}

export function levelBarClass(level) {
  return LEVEL_BAR[level] ?? LEVEL_BAR[0]
}

/** 列表筛选：等级 + 已读状态 */
export const READ_STATUS_CHIPS = [
  { value: '', label: '全部' },
  { value: 0, label: '未读' },
  { value: 1, label: '已读' }
]
