<script setup>
/** 新闻速递：标题 + 类型标签 + 相对时间，点击进详情 */
import { friendlyTime } from '@/utils/display'

defineProps({
  /** [{ id, title, newsType, startTime }] */
  items: { type: Array, default: () => [] }
})
const emit = defineEmits(['open'])

const TYPE_TEXT = { 1: '通知', 2: '活动', 3: '院动态', 4: '其他' }
const typeText = (t) => TYPE_TEXT[t || 0] || '资讯'
</script>

<template>
  <ul class="divide-y divide-slate-100">
    <li v-for="n in items" :key="n.id">
      <button
        class="group flex w-full items-center gap-2.5 py-2.5 text-left"
        @click="emit('open', n.id)"
      >
        <span class="shrink-0 rounded-md bg-brand-50 px-1.5 py-0.5 text-[10px] font-medium text-brand-700">
          {{ typeText(n.newsType) }}
        </span>
        <span class="min-w-0 flex-1 truncate text-sm transition group-hover:text-brand-600">{{ n.title }}</span>
        <span class="shrink-0 text-[11px] text-slate-400">{{ friendlyTime(n.startTime) }}</span>
      </button>
    </li>
  </ul>
</template>
