<!--
  首页侧栏的"今日待办"面板（桌面端右栏）

  纯展示：数字与跳转都从外面传，页面不需要为它写一份 if/else。
  items: [{ label, value, to, tone }]
-->
<script setup>
import { useRouter } from 'vue-router'

defineProps({
  items: { type: Array, default: () => [] },
  title: { type: String, default: '今日待办' }
})

const router = useRouter()
</script>

<template>
  <section class="card-flat p-4">
    <header class="mb-1 flex items-center justify-between">
      <h3 class="section-title">{{ title }}</h3>
      <span class="text-[11px] text-slate-300">按紧急程度排序</span>
    </header>

    <ul v-if="items.length" class="divide-y divide-slate-100">
      <li v-for="item in items" :key="item.label">
        <button
          type="button"
          class="flex w-full items-center gap-2 py-2.5 text-left transition hover:text-brand-600"
          @click="item.to && router.push(item.to)"
        >
          <span class="h-1.5 w-1.5 shrink-0 rounded-full" :class="item.tone || 'bg-slate-300'" />
          <span class="min-w-0 flex-1 truncate text-[13px] text-slate-600">{{ item.label }}</span>
          <span
            class="shrink-0 text-sm font-semibold"
            :class="item.value ? 'text-slate-900' : 'text-slate-300'"
          >
            {{ item.value }}
          </span>
          <svg
            v-if="item.to"
            class="h-3.5 w-3.5 shrink-0 text-slate-300"
            viewBox="0 0 20 20"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
          >
            <path d="m8 5 5 5-5 5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </li>
    </ul>

    <p v-else class="py-4 text-center text-xs text-slate-300">今天没有待办</p>
  </section>
</template>
