/**
 * 后台导航配置 —— 侧边栏与移动端横滑菜单的唯一数据源。
 * 加一个后台模块 = 这里加一行 + router 里加一条子路由。
 */
export const ADMIN_MENU = [
  { key: 'dashboard', label: '概览', path: '/admin', icon: '📊' },
  { key: 'news', label: '新闻', path: '/admin/news', icon: '📰' },
  { key: 'ad', label: '广告', path: '/admin/ad', icon: '🖼️' },
  { key: 'match', label: '赛事', path: '/admin/match', icon: '🏆' },
  { key: 'rank', label: '红黑榜', path: '/admin/rank', icon: '🏅' },
  { key: 'timetable', label: '课表导入', path: '/admin/timetable', icon: '📅' },
  { key: 'scholarship', label: '奖学金', path: '/admin/scholarship', icon: '🎓' }
]

export function activeAdminKey(path) {
  // 精确匹配优先，避免 /admin/news 抢走 /admin 的高亮
  const exact = ADMIN_MENU.find((item) => item.path === path)
  if (exact) return exact.key
  const prefix = ADMIN_MENU.filter((item) => item.path !== '/admin').find((item) =>
    path.startsWith(item.path)
  )
  return prefix?.key ?? 'dashboard'
}
