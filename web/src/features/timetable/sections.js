/**
 * 节次时间表 —— 首页「下一节」与课表网格共用。
 *
 * 学校作息如果不同，只改这里一处（课表页的左侧时间列也读它）。
 * 只记录每节的开始/结束时刻，不含日期。
 */

export const SECTION_TIMES = {
  1: { start: '08:00', end: '08:45' },
  2: { start: '08:55', end: '09:40' },
  3: { start: '10:00', end: '10:45' },
  4: { start: '10:55', end: '11:40' },
  5: { start: '14:00', end: '14:45' },
  6: { start: '14:55', end: '15:40' },
  7: { start: '16:00', end: '16:45' },
  8: { start: '16:55', end: '17:40' },
  9: { start: '19:00', end: '19:45' },
  10: { start: '19:55', end: '20:40' },
  11: { start: '20:50', end: '21:35' },
  12: { start: '21:45', end: '22:30' }
}

/** "08:55" → 535（当天分钟数），解析失败返回 null */
function toMinutes(hhmm) {
  if (!hhmm || !hhmm.includes(':')) return null
  const [h, m] = hhmm.split(':').map(Number)
  if (Number.isNaN(h) || Number.isNaN(m)) return null
  return h * 60 + m
}

/**
 * 从"今天的课"里挑出还没结束的那一节。
 *
 * 之前的实现直接取第一节，晚上也会显示"下一节 第1-2节"。
 * 现在按节次结束时间比较：全部上完了返回 null（调用方据此显示"今天的课已结束"）。
 */
export function pickNextCourse(courses = [], now = new Date()) {
  const nowMinutes = now.getHours() * 60 + now.getMinutes()
  const sorted = [...courses].sort((a, b) => a.sectionStart - b.sectionStart)
  for (const course of sorted) {
    const time = SECTION_TIMES[course.sectionEnd] || SECTION_TIMES[course.sectionStart]
    const endMinutes = toMinutes(time?.end)
    if (endMinutes === null || nowMinutes < endMinutes) return course
  }
  return null
}

/** 课表网格左侧列用的展示串，如 "08:00" */
export function sectionStartLabel(section) {
  return SECTION_TIMES[section]?.start ?? ''
}
