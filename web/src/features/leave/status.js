/**
 * 请假状态机 —— 状态文案、徽标配色、可执行动作的唯一出处。
 *
 * 后端只给 status 数字，页面不要自己写 if (status === 20)：
 * 加一个状态或改文案时，只改这里。
 */

export const LEAVE_STATUS = {
  10: { text: '待审批', tone: 'amber' },
  11: { text: '待上级审批', tone: 'amber' },
  20: { text: '待销假', tone: 'sky' },
  21: { text: '已销假', tone: 'emerald' },
  30: { text: '已驳回', tone: 'rose' },
  50: { text: '已取消', tone: 'slate' }
}

const TONE_CLASS = {
  amber: 'bg-amber-50 text-amber-700 ring-1 ring-amber-200',
  sky: 'bg-sky-50 text-sky-700 ring-1 ring-sky-200',
  emerald: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200',
  rose: 'bg-rose-50 text-rose-700 ring-1 ring-rose-200',
  slate: 'bg-slate-100 text-slate-600 ring-1 ring-slate-200'
}

export function leaveStatusText(status, fallback = '') {
  return LEAVE_STATUS[status]?.text ?? fallback ?? '—'
}

export function leaveStatusClass(status) {
  return TONE_CLASS[LEAVE_STATUS[status]?.tone ?? 'slate']
}

/** 列表筛选 chips：value 为空表示不传该条件 */
export const LEAVE_STATUS_CHIPS = [
  { value: '', label: '全部' },
  { value: 10, label: '待审批' },
  { value: 20, label: '待销假' },
  { value: 21, label: '已销假' },
  { value: 30, label: '已驳回' }
]

/** 学生可取消：还没进终态的单子（已驳回也允许撤销重提） */
export function canCancel(status) {
  return [10, 11, 30].includes(status)
}

/** 已批准的假条需要销假 */
export function canReturn(status) {
  return status === 20
}
