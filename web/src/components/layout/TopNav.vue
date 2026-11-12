<!--
  桌面端顶部导航（布局地基）

  只在 >=1024px 显示（lg:block），由 AppShell / PageShell 决定是否渲染，
  本组件不自己判断屏幕宽度 —— 这样详情页也能有导航，页面代码不用关心。

  包含：品牌、一级导航（TAB_ITEMS 唯一数据源）、未读红点、用户菜单。
-->
<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { TAB_ITEMS } from '@/features/navigation/tabs'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useConfirm } from '@/composables/useConfirm'

const props = defineProps({
  /** 当前一级导航 key，空表示不高亮（详情页） */
  active: { type: String, default: '' }
})

const store = useUserStore()
const app = useAppStore()
const router = useRouter()
const route = useRoute()
const { confirm } = useConfirm()

const menuOpen = ref(false)
const isAdmin = computed(() => store.roles.includes('ADMIN'))
const displayName = computed(() => store.me?.name || store.me?.account || '同学')
const initial = computed(() => (displayName.value || '?').slice(0, 1))

function go(item) {
  menuOpen.value = false
  if (route.path !== item.path) router.push(item.path)
}

async function onLogout() {
  menuOpen.value = false
  const ok = await confirm({
    title: '退出登录',
    content: '确定要退出当前账号吗？退出后需要重新输入账号密码。',
    confirmText: '退出',
    tone: 'danger'
  })
  if (!ok) return
  store.logout()
  app.reset()
  router.replace('/login')
}

/** 点击空白关掉下拉：遮罩层比 document 监听更好收拾 */
function closeMenu() {
  menuOpen.value = false
}

onMounted(() => {
  // 刷新页面时 me 为空，补一次；失败交给路由守卫处理
  if (store.isLogin && !store.me) store.fetchMe().catch(() => {})
  if (store.isLogin && !app.unreadLoaded) app.refreshUnread()
})

onBeforeUnmount(() => {
  menuOpen.value = false
})
</script>

<template>
  <header
    class="sticky top-0 z-30 hidden border-b border-slate-200/70 bg-white/90 backdrop-blur lg:block"
  >
    <div class="mx-auto flex h-[60px] max-w-content items-center gap-6 px-8">
      <!-- 品牌 -->
      <router-link to="/" class="group flex shrink-0 items-center gap-2.5">
        <span
          class="grid h-8 w-8 place-items-center rounded-xl bg-gradient-to-br from-brand-500 to-indigo-500 text-sm font-bold text-white shadow-sm transition group-hover:shadow-md"
        >
          P
        </span>
        <span class="text-[15px] font-semibold tracking-tight text-slate-900">校园成长平台</span>
      </router-link>

      <!-- 一级导航 -->
      <nav class="flex items-center gap-1" aria-label="主导航">
        <button
          v-for="item in TAB_ITEMS"
          :key="item.key"
          type="button"
          class="relative rounded-lg px-3.5 py-2 text-sm transition"
          :class="
            item.key === props.active
              ? 'bg-brand-50 font-medium text-brand-700'
              : 'text-slate-500 hover:bg-slate-50 hover:text-slate-900'
          "
          :aria-current="item.key === props.active ? 'page' : undefined"
          @click="go(item)"
        >
          {{ item.label }}
          <span
            v-if="item.key === 'message' && app.hasUnread"
            class="absolute -right-0.5 top-1 grid h-4 min-w-[16px] place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-medium leading-none text-white ring-2 ring-white"
          >
            {{ app.unreadText }}
          </span>
        </button>
      </nav>

      <!-- 右侧：后台入口 + 用户菜单 -->
      <div class="ml-auto flex items-center gap-2">
        <button
          v-if="isAdmin"
          type="button"
          class="btn-sm border border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:text-slate-900"
          @click="router.push('/admin')"
        >
          管理后台
        </button>

        <div class="relative">
          <button
            type="button"
            class="flex items-center gap-2 rounded-full border border-slate-200 bg-white py-1 pl-1 pr-2.5 transition hover:border-slate-300"
            :aria-expanded="menuOpen"
            aria-haspopup="menu"
            @click="menuOpen = !menuOpen"
          >
            <span
              class="grid h-7 w-7 place-items-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700"
            >
              {{ initial }}
            </span>
            <span class="max-w-[88px] truncate text-sm text-slate-700">{{ displayName }}</span>
            <svg
              class="h-3.5 w-3.5 text-slate-400 transition"
              :class="menuOpen ? 'rotate-180' : ''"
              viewBox="0 0 20 20"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
            >
              <path d="m5 8 5 5 5-5" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>

          <!-- 下拉菜单 -->
          <div v-if="menuOpen" class="fixed inset-0 z-30" @click="closeMenu" />
          <div
            v-if="menuOpen"
            class="absolute right-0 top-11 z-40 w-44 animate-pop-in overflow-hidden rounded-xl border border-slate-200 bg-white py-1 shadow-pop"
            role="menu"
          >
            <div class="border-b border-slate-100 px-3.5 pb-2 pt-1.5">
              <p class="truncate text-sm font-medium text-slate-900">{{ displayName }}</p>
              <p class="mt-0.5 truncate text-[11px] text-slate-400">
                {{ store.roles.join(' / ') || '—' }}
              </p>
            </div>
            <button
              type="button"
              class="flex w-full items-center gap-2 px-3.5 py-2 text-left text-sm text-slate-600 transition hover:bg-slate-50"
              role="menuitem"
              @click="menuOpen = false; router.push('/profile')"
            >
              <span class="text-[13px]">👤</span>我的主页
            </button>
            <button
              type="button"
              class="flex w-full items-center gap-2 px-3.5 py-2 text-left text-sm text-slate-600 transition hover:bg-slate-50"
              role="menuitem"
              @click="menuOpen = false; router.push('/profile/edit')"
            >
              <span class="text-[13px]">✏️</span>账号资料
            </button>
            <button
              v-if="isAdmin"
              type="button"
              class="flex w-full items-center gap-2 px-3.5 py-2 text-left text-sm text-slate-600 transition hover:bg-slate-50 lg:hidden"
              role="menuitem"
              @click="menuOpen = false; router.push('/admin')"
            >
              <span class="text-[13px]">⚙️</span>管理后台
            </button>
            <div class="my-1 h-px bg-slate-100" />
            <button
              type="button"
              class="flex w-full items-center gap-2 px-3.5 py-2 text-left text-sm text-rose-600 transition hover:bg-rose-50"
              role="menuitem"
              @click="onLogout"
            >
              <span class="text-[13px]">↩</span>退出登录
            </button>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>
