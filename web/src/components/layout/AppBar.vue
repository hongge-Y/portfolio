<!--
  移动端顶部栏（布局地基）

  只在 <1024px 显示。一级页面用它：品牌 + 页面标题 + 消息铃 + 头像。
  详情页不共用它 —— 详情页由 PageShell 的返回条负责。
-->
<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const props = defineProps({
  title: { type: String, default: '校园成长平台' },
  /** 是否显示问候语式的副标题（首页用） */
  subText: { type: String, default: '' }
})

const router = useRouter()
const store = useUserStore()
const app = useAppStore()

const displayName = computed(() => store.me?.name || store.me?.account || '同学')
const initial = computed(() => (displayName.value || '?').slice(0, 1))
</script>

<template>
  <header
    class="sticky top-0 z-30 border-b border-slate-200/70 bg-white/90 backdrop-blur lg:hidden"
  >
    <div class="flex h-14 items-center gap-2 px-4">
      <router-link to="/" class="flex min-w-0 flex-1 items-center gap-2">
        <span
          class="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-gradient-to-br from-brand-500 to-indigo-500 text-xs font-bold text-white"
        >
          P
        </span>
        <span class="min-w-0">
          <span class="block truncate text-sm font-semibold leading-tight text-slate-900">
            {{ props.title }}
          </span>
          <span v-if="props.subText" class="block truncate text-[10px] leading-tight text-slate-400">
            {{ props.subText }}
          </span>
        </span>
      </router-link>

      <button
        type="button"
        class="relative grid h-9 w-9 place-items-center rounded-xl text-slate-500 transition active:bg-slate-100"
        aria-label="站内消息"
        @click="router.push('/message')"
      >
        <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
          <path
            d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path d="M13.7 21a2 2 0 0 1-3.4 0" stroke-linecap="round" />
        </svg>
        <span
          v-if="app.hasUnread"
          class="absolute right-0.5 top-1 grid h-4 min-w-[16px] place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-medium leading-none text-white ring-2 ring-white"
        >
          {{ app.unreadText }}
        </span>
      </button>

      <button
        type="button"
        class="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700"
        aria-label="我的"
        @click="router.push('/profile')"
      >
        {{ initial }}
      </button>
    </div>
  </header>
</template>
