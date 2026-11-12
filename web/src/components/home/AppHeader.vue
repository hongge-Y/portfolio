<script setup>
/**
 * 顶部栏：品牌、未读数、当前用户、退出。
 * 纯粹展示 + 抛事件，不直接调 store / router，方便以后换到别的页面复用。
 */
defineProps({
  userName: { type: String, default: '—' },
  unread: { type: Number, default: 0 },
  /** 右侧第二行说明，例如"学生视角" */
  subText: { type: String, default: '' }
})

const emit = defineEmits(['bell', 'profile', 'logout'])
</script>

<template>
  <header class="sticky top-0 z-20 border-b border-slate-100 bg-white/90 backdrop-blur">
    <div class="mx-auto flex h-14 max-w-3xl items-center justify-between gap-3 px-4">
      <div class="flex min-w-0 items-center gap-2">
        <div class="grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-brand-500 font-bold text-white">
          P
        </div>
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold leading-tight">校园成长平台</p>
          <p v-if="subText" class="truncate text-[10px] leading-tight text-slate-400">{{ subText }}</p>
        </div>
      </div>

      <div class="flex shrink-0 items-center gap-1">
        <button
          class="relative grid h-9 w-9 place-items-center rounded-xl text-slate-500 transition hover:bg-slate-50"
          aria-label="站内消息"
          @click="emit('bell')"
        >
          🔔
          <span
            v-if="unread > 0"
            class="absolute right-0.5 top-1 grid h-4 min-w-[16px] place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-medium text-white ring-2 ring-white"
          >{{ unread > 99 ? '99+' : unread }}</span>
        </button>
        <button
          class="hidden items-center gap-1.5 rounded-xl px-2 py-1.5 text-sm text-slate-600 transition hover:bg-slate-50 sm:flex"
          @click="emit('profile')"
        >
          <span class="grid h-6 w-6 place-items-center rounded-full bg-brand-50 text-[11px] font-semibold text-brand-700">
            {{ userName.slice(0, 1) }}
          </span>
          <span class="max-w-[72px] truncate">{{ userName }}</span>
        </button>
        <button
          class="rounded-xl px-2.5 py-1.5 text-xs text-slate-400 transition hover:bg-slate-50 hover:text-slate-700"
          @click="emit('logout')"
        >
          退出
        </button>
      </div>
    </div>
  </header>
</template>
