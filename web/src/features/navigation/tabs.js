/**
 * 一级导航(tab)配置 —— 全端唯一数据源
 *
 * 桌面端顶部导航(TopNav)与移动端底部导航(TabBar)都从这里渲染，
 * 顺序、文案、路径只有这一份，改导航不用再翻两个组件。
 *
 * 新增 tab 的三步：
 * 1. 在这里加一项（key 必须与路由 meta.tab 一致）；
 * 2. 在 router/index.js 里给对应路由标 meta: { layout: 'app', tab: '<key>' };
 * 3. 图标在 components/layout/TabBar.vue 的 ICONS 里补一条 path。
 */

export const TAB_ITEMS = [
  { key: 'home', label: '首页', path: '/' },
  { key: 'message', label: '消息', path: '/message' },
  { key: 'account', label: '我的', path: '/profile' }
]

export const TAB_KEYS = TAB_ITEMS.map((item) => item.key)

export function tabPath(key) {
  return TAB_ITEMS.find((item) => item.key === key)?.path ?? '/'
}

export function findTabByPath(path) {
  return TAB_ITEMS.find((item) => item.path === path) ?? null
}

/** 判断某个路径是否是一级页面（一级页面才显示底部/顶部主导航） */
export function isTabPath(path) {
  return TAB_ITEMS.some((item) => item.path === path)
}
