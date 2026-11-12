/**
 * 角色字典 —— 后端给的是 code，界面上要中文。
 *
 * 之前每个页面各写一份 ROLE_NAME，改一次得改三处；
 * 现在统一在这里，页面只调 roleLabel()。
 */
export const ROLE_NAME = {
  ADMIN: '管理员',
  COUNSELOR: '辅导员',
  SECRETARY: '书记',
  TEACHER: '教师',
  MONITOR: '学委',
  STUDENT: '学生',
  PARENT: '家长',
  STAFF: '行政人员'
}

/** 角色排序：身份卡上按"主身份在前"显示 */
export const ROLE_ORDER = ['ADMIN', 'STAFF', 'SECRETARY', 'COUNSELOR', 'TEACHER', 'MONITOR', 'STUDENT', 'PARENT']

export function roleLabel(code) {
  return ROLE_NAME[code] ?? code ?? '—'
}

export function roleLabels(codes = []) {
  const list = [...codes].sort((a, b) => {
    const ia = ROLE_ORDER.indexOf(a)
    const ib = ROLE_ORDER.indexOf(b)
    return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib)
  })
  return list.map(roleLabel)
}

/** 身份角标配色：家长用青色，审批人用紫色，避免全是蓝的 */
export function roleTone(code) {
  if (code === 'PARENT') return 'bg-teal-50 text-teal-700 ring-teal-200'
  if (code === 'ADMIN') return 'bg-slate-900 text-white ring-slate-900'
  if (code === 'COUNSELOR' || code === 'SECRETARY') return 'bg-violet-50 text-violet-700 ring-violet-200'
  if (code === 'MONITOR') return 'bg-amber-50 text-amber-700 ring-amber-200'
  return 'bg-brand-50 text-brand-700 ring-brand-200'
}
