/**
 * 首页数据层：所有接口调用、加载状态、失败重试都收在这里，
 * 页面组件只负责"把数据画出来"，不碰 axios。
 *
 * 约定：每个板块一个独立 status（loading/ready/empty/error），
 * 一个接口挂了不影响其他板块渲染 —— 用 Promise.allSettled 而不是 all。
 */
import { computed, reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'
import { contentApi } from '@/api/content'
import { messageApi } from '@/api/message'
import { eduApi } from '@/api/edu'
import { leaveApi } from '@/api/leave'
import { humanMessage } from '@/utils/crypto'
import { pickNextCourse } from '@/features/timetable/sections'

export function useHomeData() {
  const store = useUserStore()

  const health = ref(null)
  const ads = ref([])
  const news = ref([])
  const matches = ref([])
  const ranks = ref([])
  const unread = ref(0)
  const pendingApprove = ref(0)
  /** 本周课表（原始），首页只取"今天"的部分 */
  const timetable = ref([])
  const termWeek = ref(null)

  const status = reactive({
    ads: 'loading',
    news: 'loading',
    match: 'loading',
    rank: 'loading',
    today: 'loading'
  })
  const errors = reactive({})

  function mark(key, empty, err) {
    if (err) {
      status[key] = 'error'
      errors[key] = humanMessage(err?.message, '网络异常，请稍后重试')
    } else {
      status[key] = empty ? 'empty' : 'ready'
      errors[key] = ''
    }
  }

  /** 今天的课（weekDay: 1=周一 … 7=周日，与后端一致） */
  const todayCourses = computed(() => {
    const wd = new Date().getDay() || 7
    return timetable.value
      .filter((c) => c.weekDay === wd)
      .sort((a, b) => a.sectionStart - b.sectionStart)
  })

  /** 下一节课：今天还没结束的最近一节，全上完了就 null */
  const nextCourse = computed(() => pickNextCourse(todayCourses.value))

  async function loadHeader() {
    if (!store.me) {
      try {
        await store.fetchMe()
      } catch {
        /* 401 会由拦截器跳登录 */
      }
    }
    const [h, u] = await Promise.allSettled([authApi.health(), messageApi.unread()])
    health.value = h.status === 'fulfilled' ? h.value : null
    unread.value = u.status === 'fulfilled' ? u.value?.total ?? 0 : 0
  }

  async function loadAds() {
    status.ads = 'loading'
    try {
      ads.value = await contentApi.adPortal()
      mark('ads', ads.value.length === 0)
    } catch (e) {
      mark('ads', true, e)
    }
  }

  async function loadNews() {
    status.news = 'loading'
    try {
      const res = await contentApi.newsPortal(1, 4)
      news.value = res.list || []
      mark('news', news.value.length === 0)
    } catch (e) {
      mark('news', true, e)
    }
  }

  async function loadMatches() {
    status.match = 'loading'
    try {
      const res = await contentApi.matchPortal(1, 3)
      matches.value = res.list || []
      mark('match', matches.value.length === 0)
    } catch (e) {
      mark('match', true, e)
    }
  }

  async function loadRanks() {
    status.rank = 'loading'
    try {
      const res = await contentApi.rankPortal(undefined, 1, 5)
      ranks.value = res.list || []
      mark('rank', ranks.value.length === 0)
    } catch (e) {
      mark('rank', true, e)
    }
  }

  /**
   * 今日课程：家长账号没有课表权限，后端会返回 emptyReason，
   * 这种情况按"空"处理，不弹错误。
   */
  async function loadToday() {
    status.today = 'loading'
    try {
      const res = await eduApi.timetable()
      timetable.value = res.items || []
      termWeek.value = { term: res.term, weekNo: res.weekNo }
      mark('today', todayCourses.value.length === 0)
    } catch (e) {
      mark('today', true, e)
    }
  }

  /** 待我审批（仅审批人）：一个 count 接口就够，不要拉整页 */
  async function loadPendingApprove() {
    if (!store.hasPerm('leave:approve')) {
      pendingApprove.value = 0
      return
    }
    try {
      const res = await leaveApi.approvePage({ pageNum: 1, pageSize: 1, searchModel: {} })
      pendingApprove.value = res.total || 0
    } catch {
      pendingApprove.value = 0
    }
  }

  /** 首屏：头部 + 各板块并行，谁先回来谁先渲染 */
  async function loadAll() {
    await loadHeader()
    await Promise.allSettled([
      loadAds(),
      loadNews(),
      loadMatches(),
      loadRanks(),
      loadToday(),
      loadPendingApprove()
    ])
  }

  return {
    store,
    health,
    ads,
    news,
    matches,
    ranks,
    unread,
    pendingApprove,
    timetable,
    termWeek,
    todayCourses,
    nextCourse,
    status,
    errors,
    loadAll,
    loadHeader,
    loadAds,
    loadNews,
    loadMatches,
    loadRanks,
    loadToday
  }
}
