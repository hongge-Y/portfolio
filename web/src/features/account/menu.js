/**
 * 个人中心菜单 —— "我的"页与桌面分栏共用的唯一数据源。
 *
 * 动态文案（绑定状态、家长/学生身份）由页面按 key 覆盖，
 * 这里只放不会变的部分，避免菜单在页面里被复制多份。
 */

export const MENU_GROUPS = {
  1: '账号',
  2: '绑定',
  3: '关系与身份'
}

export const ACCOUNT_MENU = [
  { key: 'profile', group: 1, title: '账号资料', desc: '昵称、生日、小宠', icon: '👤', path: '/profile/edit' },
  {
    key: 'security',
    group: 1,
    title: '账号安全',
    desc: '登录记录 · 风险预警',
    icon: '🛡️',
    path: '/profile/security'
  },
  {
    key: 'password',
    group: 1,
    title: '修改密码',
    desc: '字母 + 数字，6-20 位',
    icon: '🔑',
    path: '/profile/password'
  },
  { key: 'phone', group: 2, title: '绑定手机', desc: '', icon: '📱', path: '/profile/bind/phone' },
  { key: 'mail', group: 2, title: '绑定邮箱', desc: '', icon: '✉️', path: '/profile/bind/mail' },
  {
    key: 'org',
    group: 3,
    title: '组织与角色',
    desc: '我的班级、身份与权限',
    icon: '🏫',
    path: '/profile/org'
  },
  {
    key: 'relative',
    group: 3,
    title: '家长与学生关联',
    desc: '',
    icon: '👨‍👩‍👧',
    path: '/relative'
  }
]

export function menuOfGroup(group) {
  return ACCOUNT_MENU.filter((item) => item.group === group)
}

export function findMenu(key) {
  return ACCOUNT_MENU.find((item) => item.key === key) ?? null
}
