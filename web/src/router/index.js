/**
 * 路由表 —— 全站结构只在这里定义
 *
 * 约定（配合 App.vue 的布局分发）：
 *   meta.layout = 'app'   一级页面：顶部导航 + 底部 tab
 *   meta.layout = 'page'  二级页面：返回条 + 标题（title / hint / back）
 *   meta.layout = 'blank' 登录等整屏页面
 *   meta.layout = 'admin' 管理后台
 *   meta.tab             一级页面对应的 tab key（features/navigation/tabs.js）
 *   meta.size            内容容器宽度（narrow / form / wide）
 *
 * 加一个页面 = 加一条路由 + meta，不需要动布局，页面模板里也不写导航。
 */
import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/utils/request'
import { useUserStore } from '@/stores/user'

const APP_TITLE = '校园成长平台'

/** 二级页的公共 meta，少写重复字段 */
const page = (title, back = '/', extra = {}) => ({ layout: 'page', auth: true, title, back, ...extra })

/** 后台页的公共 meta */
const admin = (title, extra = {}) => ({ layout: 'admin', auth: true, admin: true, title, ...extra })

const routes = [
  // ---------- 空白壳 ----------
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/Login.vue'),
    meta: { layout: 'blank', title: '登录', ai: false }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/pages/Register.vue'),
    meta: { layout: 'blank', title: '学生注册', ai: false }
  },

  // ---------- 一级页面（AppShell） ----------
  {
    path: '/',
    name: 'home',
    component: () => import('@/pages/Home.vue'),
    meta: { layout: 'app', tab: 'home', auth: true, title: '首页', barTitle: '校园成长平台' }
  },
  {
    path: '/message',
    name: 'messageList',
    component: () => import('@/pages/message/MessageList.vue'),
    meta: { layout: 'app', tab: 'message', auth: true, title: '消息', barTitle: '消息' }
  },
  {
    path: '/profile',
    name: 'account',
    component: () => import('@/pages/profile/Account.vue'),
    meta: { layout: 'app', tab: 'account', auth: true, title: '我的', barTitle: '我的' }
  },

  // ---------- 消息详情 ----------
  {
    path: '/message/:id',
    name: 'messageDetail',
    component: () => import('@/pages/message/MessageDetail.vue'),
    meta: page('消息详情', '/message')
  },

  // ---------- 新手引导（整屏自绘，不套壳） ----------
  {
    path: '/guide/pet',
    name: 'guidePet',
    component: () => import('@/pages/guide/SelectPet.vue'),
    meta: { layout: 'blank', auth: true, title: '选择小宠', ai: false, skipGuide: true }
  },
  {
    path: '/guide/gift',
    name: 'guideGift',
    component: () => import('@/pages/guide/GiftBox.vue'),
    meta: { layout: 'blank', auth: true, title: '惊喜礼盒', ai: false, skipGuide: true }
  },

  // ---------- 内容：新闻 / 赛事 / 红黑榜 ----------
  {
    path: '/news',
    name: 'newsList',
    component: () => import('@/pages/news/NewsList.vue'),
    meta: page('新闻速递', '/')
  },
  {
    path: '/news/:id',
    name: 'newsDetail',
    component: () => import('@/pages/news/NewsDetail.vue'),
    meta: page('新闻详情', '/news', { size: 'narrow' })
  },
  {
    path: '/match',
    name: 'matchList',
    component: () => import('@/pages/match/MatchList.vue'),
    meta: page('赛事风云', '/')
  },
  {
    path: '/match/:id',
    name: 'matchDetail',
    component: () => import('@/pages/match/MatchDetail.vue'),
    meta: page('赛事详情', '/match', { size: 'narrow' })
  },
  {
    path: '/rank',
    name: 'rankList',
    component: () => import('@/pages/rank/RankList.vue'),
    meta: page('红黑榜', '/')
  },
  {
    path: '/rank/:id',
    name: 'rankDetail',
    component: () => import('@/pages/rank/RankDetail.vue'),
    meta: page('红黑榜详情', '/rank', { size: 'narrow' })
  },

  // ---------- 课表 ----------
  {
    path: '/timetable',
    name: 'timetable',
    component: () => import('@/pages/timetable/Timetable.vue'),
    meta: page('我的课表', '/', { size: 'wide' })
  },
  {
    path: '/timetable/eval',
    name: 'courseEval',
    component: () => import('@/pages/timetable/CourseEval.vue'),
    meta: page('课程评价', '/timetable', { size: 'narrow' })
  },

  // ---------- 请假 ----------
  {
    path: '/leave',
    name: 'leaveList',
    component: () => import('@/pages/leave/LeaveList.vue'),
    meta: page('请假', '/')
  },
  {
    path: '/leave/new',
    name: 'leaveNew',
    component: () => import('@/pages/leave/LeaveEdit.vue'),
    meta: page('发起请假', '/leave', { size: 'narrow' })
  },
  {
    path: '/leave/approve',
    name: 'leaveApprove',
    component: () => import('@/pages/leave/ApproveList.vue'),
    meta: page('待我审批', '/')
  },
  {
    path: '/leave/:id',
    name: 'leaveDetail',
    component: () => import('@/pages/leave/LeaveDetail.vue'),
    meta: page('请假详情', '/leave')
  },

  // ---------- 奖学金 ----------
  {
    path: '/scholarship',
    name: 'scholarship',
    component: () => import('@/pages/scholarship/Scholarship.vue'),
    meta: page('奖学金', '/')
  },

  // ---------- 个人中心子页 ----------
  {
    path: '/profile/edit',
    name: 'profileEdit',
    component: () => import('@/pages/profile/ProfileEdit.vue'),
    meta: page('账号资料', '/profile', { size: 'form' })
  },
  {
    path: '/profile/security',
    name: 'profileSecurity',
    component: () => import('@/pages/profile/Security.vue'),
    meta: page('账号安全', '/profile')
  },
  {
    path: '/profile/bind/:type',
    name: 'profileBind',
    component: () => import('@/pages/profile/BindAccount.vue'),
    meta: page('绑定账号', '/profile', { size: 'form' })
  },
  {
    path: '/profile/password',
    name: 'profilePassword',
    component: () => import('@/pages/profile/Password.vue'),
    meta: page('修改密码', '/profile', { size: 'form' })
  },
  {
    path: '/profile/org',
    name: 'profileOrg',
    component: () => import('@/pages/profile/Org.vue'),
    meta: page('组织与角色', '/profile')
  },
  {
    path: '/relative',
    name: 'relative',
    component: () => import('@/pages/profile/Relative.vue'),
    meta: page('家长与学生关联', '/profile')
  },

  // ---------- 管理后台 ----------
  {
    path: '/admin',
    name: 'adminDashboard',
    component: () => import('@/pages/admin/Dashboard.vue'),
    meta: admin('概览')
  },
  {
    path: '/admin/news',
    name: 'adminNews',
    component: () => import('@/pages/admin/News.vue'),
    meta: admin('新闻管理')
  },
  {
    path: '/admin/ad',
    name: 'adminAd',
    component: () => import('@/pages/admin/Ad.vue'),
    meta: admin('广告位管理')
  },
  {
    path: '/admin/match',
    name: 'adminMatch',
    component: () => import('@/pages/admin/Match.vue'),
    meta: admin('赛事管理')
  },
  {
    path: '/admin/rank',
    name: 'adminRank',
    component: () => import('@/pages/admin/Rank.vue'),
    meta: admin('红黑榜管理')
  },
  {
    path: '/admin/timetable',
    name: 'adminTimetable',
    component: () => import('@/pages/admin/Timetable.vue'),
    meta: admin('课表导入')
  },
  {
    path: '/admin/scholarship',
    name: 'adminScholarship',
    component: () => import('@/pages/admin/Scholarship.vue'),
    meta: admin('奖学金管理')
  },

  // ---------- 兜底 ----------
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('@/pages/NotFound.vue'),
    meta: { layout: 'page', title: '页面不存在', back: '/', ai: false }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  /** 切页回到顶部：详情页之间跳转时不会残留在上一页的滚动位置 */
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    return { top: 0 }
  }
})

router.beforeEach(async (to) => {
  if (to.meta.auth && !getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && getToken()) {
    return { path: '/' }
  }

  // 没走完引导的新账号先走引导（选宠 → 开礼盒）。
  // skipGuide 的页面（引导自身、登录、404）不拦，否则会自己跳自己。
  // guideDone 只在 /v1/profile/me 里，store 内部缓存，整个会话只多一次请求。
  if (getToken() && to.meta.auth && !to.meta.skipGuide) {
    const store = useUserStore()
    try {
      const profile = await store.fetchProfile()
      if (profile && !Number(profile.guideDone)) return { path: '/guide/pet' }
    } catch {
      /* 资料拉不到（网络/401）不阻断导航，交给页面自己的三态 */
    }
  }

  // 刷新/直链条进入时 store.me 是空的，权限相关的按钮（审批、AI 建议、后台入口）
  // 会按“无权限”渲染而整块消失。这里统一补一次 /auth/me，登录态有效就一定有 me。
  if (getToken() && to.meta.auth) {
    const store = useUserStore()
    if (!store.me) {
      try {
        await store.fetchMe()
      } catch {
        /* 拉不到就交给页面三态，不阻断导航 */
      }
    }
  }

  if (to.meta.admin) {
    const store = useUserStore()
    if (!store.me) {
      try {
        await store.fetchMe()
      } catch {
        return { path: '/login' }
      }
    }
    if (!store.me?.roles?.includes('ADMIN')) return { path: '/' }
  }
  return true
})

/** 浏览器标签页标题跟着路由走，答辩演示时切页能看出层级 */
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
})

export default router
