<!--
  移动端底部导航（布局地基）

  只在 <1024px 显示；桌面由 TopNav 承担同样三个入口。
  入口来自 features/navigation/tabs.js，图标在这里按 key 映射（带 path 的内联 SVG，
  不引图标库 —— 三个图标引一整个库不划算）。
-->
<script setup>
import { useRouter } from 'vue-router'
import { TAB_ITEMS } from '@/features/navigation/tabs'
import { useAppStore } from '@/stores/app'

const props = defineProps({
  active: { type: String, default: '' }
})

const router = useRouter()
const app = useAppStore()

const ICONS = {
  home: 'M3 10.7 12 3.4l9 7.3V20a1 1 0 0 1-1 1h-5.2v-6.2H9.2V21H4a1 1 0 0 1-1-1z',
  message: 'M12 3a9 9 0 0 1 0 18H6.5L3 23l.9-4.6A9 9 0 0 1 12 3z',
  account: 'M12 12.2a4.1 4.1 0 1 0 0-8.2 4.1 4.1 0 0 0 0 8.2zm0 2c-4.2 0-7.4 2.3-7.4 5.2V21h14.8v-1.6c0-2.9-3.2-5.2-7.4-5.2z'
}

function go(item) {
  if (item.key === props.active) return
  router.push(item.path)
}
</script>

<template>
  <nav
    class="fixed inset-x-0 bottom-0 z-30 border-t border-slate-200/80 bg-white/95 backdrop-blur lg:hidden"
    style="padding-bottom: env(safe-area-inset-bottom)"
    aria-label="底部导航"
  >
    <div class="flex h-16 items-stretch">
      <button
        v-for="item in TAB_ITEMS"
        :key="item.key"
        type="button"
        class="relative flex flex-1 flex-col items-center justify-center gap-1 transition"
        :class="item.key === props.active ? 'text-brand-600' : 'text-slate-400'"
        :aria-current="item.key === props.active ? 'page' : undefined"
        @click="go(item)"
      >
        <span class="relative">
          <svg class="h-[22px] w-[22px]" viewBox="0 0 24 24" fill="currentColor">
            <path :d="ICONS[item.key]" />
          </svg>
          <span
            v-if="item.key === 'message' && app.hasUnread"
            class="absolute -right-2 -top-1 grid h-4 min-w-[16px] place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-medium leading-none text-white ring-2 ring-white"
          >
            {{ app.unreadText }}
          </span>
        </span>
        <span class="text-[10px] leading-none" :class="item.key === props.active ? 'font-medium' : ''">
          {{ item.label }}
        </span>
      </button>
    </div>
  </nav>
</template>
