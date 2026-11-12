<script setup>
/**
 * 首页 = 组装层（套 AppShell，页面里不再画导航）。
 *
 * 结构：桌面两栏（主内容 + 侧栏待办/状态），移动端单栏依次排列。
 * 数据在 composables/useHomeData.js，模块在 components/home/*，
 * 这里只做三件事：按角色算入口、把数据接到组件上、处理跳转。
 * 以后加一个板块 = 写一个组件 + 这里放一行，不动数据层。
 */
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import HeroCard from '@/components/home/HeroCard.vue'
import EntryGrid from '@/components/home/EntryGrid.vue'
import QuickAskCard from '@/components/home/QuickAskCard.vue'
import AdCarousel from '@/components/home/AdCarousel.vue'
import NewsFeed from '@/components/home/NewsFeed.vue'
import MatchBoard from '@/components/home/MatchBoard.vue'
import RankBoard from '@/components/home/RankBoard.vue'
import TodoPanel from '@/components/home/TodoPanel.vue'
import ServiceStatus from '@/components/home/ServiceStatus.vue'
import SectionCard from '@/components/common/SectionCard.vue'
import DataState from '@/components/common/DataState.vue'
import { useHomeData } from '@/composables/useHomeData'
import { useAiPanel } from '@/composables/useAiPanel'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const app = useAppStore()
const { show: showAi, ask: askAi } = useAiPanel()

const {
  store,
  health,
  ads,
  news,
  matches,
  ranks,
  pendingApprove,
  termWeek,
  todayCourses,
  nextCourse,
  status,
  errors,
  loadAll,
  loadNews,
  loadMatches,
  loadRanks,
  loadToday
} = useHomeData()

/** 入口按角色动态拼：家长没有请假/课表/奖学金，审批人多"待审批"，管理员多后台 */
const entries = computed(() => {
  const list = [
    { key: 'ai', title: 'AI 助手', desc: '问流程 · 帮写理由', icon: '✨', highlight: true }
  ]

  if (store.roles.includes('ADMIN')) {
    list.push({ key: 'admin', title: '管理后台', desc: '内容与评审', icon: '⚙️', to: '/admin' })
  }
  if (store.hasPerm('leave:approve') && pendingApprove.value > 0) {
    list.push({
      key: 'approve',
      title: '待审批',
      desc: '请假审批',
      icon: '✅',
      to: '/leave/approve',
      badge: pendingApprove.value
    })
  }
  if (!store.isParent) {
    list.push({ key: 'leave', title: '请假', desc: '申请 / 审批 / 销假', icon: '📝', to: '/leave' })
    list.push({ key: 'timetable', title: '课表', desc: '本周课程 · 评价', icon: '📅', to: '/timetable' })
    list.push({ key: 'eval', title: '课程评价', desc: '给本学期课程打分', icon: '⭐', to: '/timetable/eval' })
  }
  list.push({ key: 'message', title: '消息', desc: '站内信 · 待办', icon: '🔔', to: '/message', badge: app.unread })
  if (!store.isParent) {
    list.push({ key: 'scholarship', title: '奖学金', desc: '申请 / 公示', icon: '🎓', to: '/scholarship' })
  }
  list.push({ key: 'rank', title: '红黑榜', desc: '成长档案', icon: '🏅', to: '/rank' })
  list.push({ key: 'account', title: '我的', desc: '资料 · 安全 · 绑定', icon: '👤', to: '/profile' })
  return list
})

/** 家长与管理员不看"今日课程" */
const showToday = computed(() => !store.isParent)

/** 主视觉指标：不同角色关心的事不一样，HeroCard 只管画 */
const metrics = computed(() => {
  const list = [{ label: '未读消息', value: app.unread, to: '/message' }]

  if (store.isParent) {
    list.push({ label: '孩子记录', value: ranks.value.length, to: '/rank' })
    list.push({ label: '关注孩子', value: '管理', to: '/relative' })
    return list
  }

  list.push({ label: '今日课程', value: todayCourses.value.length, to: '/timetable' })

  if (store.hasPerm('leave:approve') && pendingApprove.value > 0) {
    list.push({ label: '待我审批', value: pendingApprove.value, to: '/leave/approve', highlight: true })
  } else if (store.roles.includes('ADMIN')) {
    list.push({ label: '管理后台', value: '进入', to: '/admin' })
  } else {
    list.push({ label: '请假', value: '去申请', to: '/leave' })
  }
  return list
})

/** 侧栏待办：只有真的有数字才显得有意义，0 也保留但置灰 */
const todos = computed(() => {
  const list = []
  if (store.hasPerm('leave:approve')) {
    list.push({
      label: '待我审批的请假',
      value: pendingApprove.value,
      to: '/leave/approve',
      tone: pendingApprove.value > 0 ? 'bg-rose-500' : 'bg-slate-200'
    })
  }
  list.push({
    label: '未读站内消息',
    value: app.unread,
    to: '/message',
    tone: app.unread > 0 ? 'bg-amber-400' : 'bg-slate-200'
  })
  if (showToday.value && nextCourse.value) {
    list.push({
      label: `下一节：${nextCourse.value.courseName}`,
      value: `第${nextCourse.value.sectionStart}-${nextCourse.value.sectionEnd}节`,
      to: '/timetable',
      tone: 'bg-brand-400'
    })
  }
  if (!store.isParent) {
    list.push({
      label: '红黑榜记录',
      value: ranks.value.length,
      to: '/rank',
      tone: 'bg-emerald-400'
    })
  }
  return list
})

const heroName = computed(() => store.me?.name || store.me?.account || '同学')

function onEntry(entry) {
  if (entry.key === 'ai') return showAi()
  if (entry.to) router.push(entry.to)
}

function onAd(ad) {
  if (ad.linkType === 1 && ad.link) return router.push(ad.link)
  if (ad.linkType === 2 && ad.link) return window.open(ad.link, '_blank', 'noopener')
}

const go = (path) => router.push(path)

onMounted(() => {
  void loadAll()
})
</script>

<template>
  <div class="space-y-4 lg:space-y-5">
    <!-- 1. 主视觉：问候 / 日期 / 指标 / 下一节课 -->
    <HeroCard
      :name="heroName"
      :employee-no="store.me?.employeeNo"
      :roles="store.roles"
      :metrics="metrics"
      :next-course="nextCourse"
      :today-count="todayCourses.length"
      :term-week="termWeek"
      :loading="status.today === 'loading'"
      @go="go"
    />

    <!-- 2. 桌面两栏：主内容 + 侧栏 -->
    <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start lg:gap-5">
      <div class="min-w-0 space-y-4 lg:space-y-5">
        <!-- 功能入口 -->
        <EntryGrid :entries="entries" @select="onEntry" />

        <!-- AI 快捷提问 -->
        <QuickAskCard @ask="askAi" />

        <!-- 今日课程 -->
        <SectionCard
          v-if="showToday"
          title="今日课程"
          :hint="termWeek ? `第 ${termWeek.weekNo} 教学周` : ''"
          more-text="完整课表"
          @more="go('/timetable')"
        >
          <DataState
            :status="status.today"
            :error-text="errors.today"
            empty-text="今天没有排课，可以安排自习"
            icon="📅"
            :rows="2"
            @retry="loadToday()"
          >
            <ul class="grid gap-2 sm:grid-cols-2">
              <li
                v-for="c in todayCourses"
                :key="c.id"
                class="flex items-center gap-2.5 rounded-xl border border-slate-100 px-3 py-2.5"
              >
                <span class="shrink-0 rounded-md bg-brand-50 px-1.5 py-0.5 text-[11px] font-medium text-brand-700">
                  第{{ c.sectionStart }}-{{ c.sectionEnd }}节
                </span>
                <span class="min-w-0 flex-1 truncate text-sm">{{ c.courseName }}</span>
                <span class="shrink-0 text-[11px] text-slate-400">{{ c.location || '—' }}</span>
              </li>
            </ul>
          </DataState>
        </SectionCard>

        <!-- 新闻 / 赛事 -->
        <div class="grid gap-4 md:grid-cols-2 lg:gap-5">
          <SectionCard title="新闻速递" more-text="更多" @more="go('/news')">
            <DataState
              :status="status.news"
              :error-text="errors.news"
              empty-text="暂无可见新闻"
              icon="📰"
              @retry="loadNews()"
            >
              <NewsFeed :items="news" @open="(id) => go(`/news/${id}`)" />
            </DataState>
          </SectionCard>

          <SectionCard title="赛事风云" more-text="更多" @more="go('/match')">
            <DataState
              :status="status.match"
              :error-text="errors.match"
              empty-text="暂无赛事"
              icon="🏆"
              @retry="loadMatches()"
            >
              <MatchBoard :items="matches" @open="(id) => go(`/match/${id}`)" />
            </DataState>
          </SectionCard>
        </div>

        <!-- 红黑榜 -->
        <SectionCard
          title="红黑榜"
          :hint="store.isParent ? '仅显示可对家长公开的记录' : '我的成长档案'"
          more-text="全部"
          @more="go('/rank')"
        >
          <DataState
            :status="status.rank"
            :error-text="errors.rank"
            empty-text="还没有红黑榜记录"
            icon="🏅"
            @retry="loadRanks()"
          >
            <RankBoard :items="ranks" :is-parent="store.isParent" @open="(id) => go(`/rank/${id}`)" />
          </DataState>
        </SectionCard>
      </div>

      <!-- 侧栏：桌面吸顶，移动端排到主内容之后 -->
      <aside class="min-w-0 space-y-4 lg:sticky lg:top-[76px]">
        <TodoPanel :items="todos" />
        <AdCarousel v-if="status.ads === 'ready'" :ads="ads" @open="onAd" />
        <ServiceStatus :health="health" :term-week="termWeek" />
      </aside>
    </div>
  </div>
</template>
