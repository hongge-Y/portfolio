<!--
  一级页面外壳 AppShell（布局地基）

  职责：抹平桌面与移动端的形态差异，让业务页面不必关心自己跑在哪种形态下。
  - 桌面（>=1024px）：TopNav + 内容限宽居中
  - 移动：AppBar + 内容 + 底部 TabBar

  用法（router meta 里标 layout: 'app'，App.vue 自动套壳，页面模板里不写壳）：
    一级页 = 首页 / 消息 / 我的

  详情页请用 PageShell（有返回条），登录页用 BlankLayout。
-->
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { TAB_ITEMS } from '@/features/navigation/tabs'
import TopNav from '@/components/layout/TopNav.vue'
import AppBar from '@/components/layout/AppBar.vue'
import TabBar from '@/components/layout/TabBar.vue'
import PageContainer from '@/components/layout/PageContainer.vue'

const props = defineProps({
  /** 由路由 meta.tab 传入；缺省时按路径反查 */
  tab: { type: String, default: '' }
})

const route = useRoute()
const store = useUserStore()
const app = useAppStore()

const activeTab = computed(() => {
  if (props.tab) return props.tab
  return TAB_ITEMS.find((item) => item.path === route.path)?.key ?? ''
})

const barTitle = computed(() => route.meta.barTitle || '校园成长平台')
const barSubText = computed(() => {
  if (route.meta.barSub) return route.meta.barSub
  if (!store.me) return ''
  if (store.isParent) return '家长视角'
  if (store.hasPerm('leave:approve')) return '审批人视角'
  if (store.roles.includes('ADMIN')) return '管理员视角'
  return '学生视角'
})

// 未读数：外壳统一拉一次，页面不再各自请求（首页/消息页只是读 store）
if (store.isLogin && !app.unreadLoaded) void app.refreshUnread()
</script>

<template>
  <div class="min-h-screen">
    <TopNav :active="activeTab" />
    <AppBar :title="barTitle" :sub-text="barSubText" />

    <main class="has-tabbar">
      <PageContainer>
        <slot />
      </PageContainer>
    </main>

    <TabBar :active="activeTab" />
  </div>
</template>
