<!--
  管理后台外壳 AdminLayout

  与 C 端共用同一套布局机制（App.vue 按 meta.layout 选壳），
  差异只在于：深色侧边栏 + 内容区，没有底部 tab。

  菜单来自 features/navigation/adminMenu.js，加模块只改那个文件 + 路由。
  页面内容由 App.vue 的 router-view 通过默认插槽传入，所以这里是 <slot /> 而非 <router-view />。
-->
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ADMIN_MENU, activeAdminKey } from '@/features/navigation/adminMenu'
import { useConfirm } from '@/composables/useConfirm'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const { confirm } = useConfirm()

const currentKey = computed(() => activeAdminKey(route.path))
const current = computed(() => ADMIN_MENU.find((m) => m.key === currentKey.value) || ADMIN_MENU[0])

function go(item) {
  if (route.path !== item.path) router.push(item.path)
}

async function logout() {
  const ok = await confirm({
    title: '退出登录',
    content: '确定要退出管理后台吗？',
    confirmText: '退出',
    tone: 'danger'
  })
  if (!ok) return
  store.logout()
  router.replace('/login')
}
</script>

<template>
  <div class="flex min-h-screen">
    <!-- 侧边栏（>=1024 显示） -->
    <aside
      class="fixed inset-y-0 left-0 hidden w-56 flex-col border-r border-slate-800/60 bg-slate-900 text-slate-300 lg:flex"
    >
      <div class="flex h-[60px] items-center gap-2.5 border-b border-slate-800 px-4">
        <span class="grid h-7 w-7 place-items-center rounded-lg bg-brand-500 text-xs font-bold text-white">
          P
        </span>
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-white">管理后台</p>
          <p class="truncate text-[10px] text-slate-500">校园成长平台 · 内容与评审</p>
        </div>
      </div>

      <nav class="flex-1 space-y-1 p-2.5">
        <button
          v-for="item in ADMIN_MENU"
          :key="item.key"
          type="button"
          class="flex h-9 w-full items-center gap-2 rounded-lg px-3 text-left text-[13px] transition"
          :class="
            currentKey === item.key
              ? 'bg-brand-500 font-medium text-white'
              : 'text-slate-300 hover:bg-slate-800 hover:text-white'
          "
          @click="go(item)"
        >
          <span class="text-[13px]">{{ item.icon }}</span>
          {{ item.label }}
        </button>
      </nav>

      <div class="space-y-2 border-t border-slate-800 p-3 text-xs">
        <div class="min-w-0">
          <p class="truncate font-medium text-slate-200">{{ store.me?.name || '管理员' }}</p>
          <p class="truncate text-[10px] text-slate-500">{{ store.me?.account }}</p>
        </div>
        <button
          type="button"
          class="flex w-full items-center gap-1.5 rounded-md px-1 py-1 text-slate-400 transition hover:text-white"
          @click="router.push('/')"
        >
          ← 回到前台
        </button>
        <button
          type="button"
          class="flex w-full items-center gap-1.5 rounded-md px-1 py-1 text-slate-400 transition hover:text-rose-400"
          @click="logout"
        >
          退出登录
        </button>
      </div>
    </aside>

    <div class="min-w-0 flex-1 lg:pl-56">
      <header class="sticky top-0 z-20 border-b border-slate-200/70 bg-white/90 backdrop-blur">
        <div class="mx-auto flex h-14 w-full max-w-[1040px] items-center gap-3 px-4 lg:h-[60px] lg:px-8">
          <span class="grid h-7 w-7 place-items-center rounded-lg bg-slate-900 text-xs font-bold text-white lg:hidden">
            P
          </span>
          <h1 class="min-w-0 flex-1 truncate text-[15px] font-semibold text-slate-900">
            {{ current.label }}
          </h1>
          <!-- 侧边栏已有落款区，这里只在小屏补一份，避免同一动作出现两遍 -->
          <button type="button" class="btn-ghost-sm lg:hidden" @click="router.push('/')">回到前台</button>
          <button type="button" class="btn-ghost-sm text-rose-600 hover:text-rose-700 lg:hidden" @click="logout">
            退出
          </button>
        </div>

        <!-- 移动端模块切换 -->
        <div class="mx-auto flex w-full max-w-[1040px] gap-1.5 overflow-x-auto px-4 pb-2.5 lg:hidden">
          <button
            v-for="item in ADMIN_MENU"
            :key="item.key"
            type="button"
            class="chip shrink-0"
            :class="currentKey === item.key ? 'chip-on' : 'chip-off'"
            @click="go(item)"
          >
            {{ item.label }}
          </button>
        </div>
      </header>

      <main class="mx-auto w-full max-w-[1040px] px-4 py-4 lg:px-8 lg:py-6">
        <slot />
      </main>
    </div>
  </div>
</template>
