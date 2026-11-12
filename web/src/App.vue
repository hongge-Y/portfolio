<script setup>
/**
 * 应用根组件 —— 布局分发层。
 *
 * 页面自己不再画导航与返回条：路由 meta.layout 决定套哪个壳，
 * 这里只做三件事：选壳、切页动画、挂全局浮层（AI 助手 / 确认弹窗）。
 *
 * meta 约定（router/index.js）：
 *   layout: 'app'    一级页面（首页/消息/我的）→ AppShell：TopNav + TabBar
 *   layout: 'page'   二级页面 → PageShell：返回条 + 标题
 *   layout: 'blank'  登录 / 免登录分享 → 不套壳
 *   layout: 'admin'  管理后台 → AdminLayout
 *   title / hint / back / tab —— 由壳读取
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import PageShell from '@/layouts/PageShell.vue'
import BlankLayout from '@/layouts/BlankLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import AiAssistant from '@/components/AiAssistant.vue'
import ConfirmHost from '@/components/common/ConfirmHost.vue'
import { getToken } from '@/utils/request'

const LAYOUTS = {
  app: AppShell,
  page: PageShell,
  blank: BlankLayout,
  admin: AdminLayout
}

const route = useRoute()

/** 未知 layout 一律按二级页处理：至少有返回条，不会出现"裸页" */
const layout = computed(() => LAYOUTS[route.meta.layout] || PageShell)

/** 登录页、免登录分享页不显示 AI 浮球（meta.ai === false 也关掉） */
const showAi = computed(() => route.meta.ai !== false && route.path !== '/login' && !!getToken())
</script>

<template>
  <!-- 壳的默认插槽里放 router-view，页面因此"自动"获得导航与统一宽度 -->
  <component :is="layout" v-bind="route.meta.layoutProps || {}">
    <router-view v-slot="{ Component }">
      <transition name="fade-up" mode="out-in">
        <!-- 页面根节点必须是单个元素：以组件为根时 Transition 无法动画，会卡在进入态（透明） -->
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </component>

  <AiAssistant v-if="showAi" />
  <ConfirmHost />
</template>
