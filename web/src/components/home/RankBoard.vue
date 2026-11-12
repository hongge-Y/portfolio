<script setup>
/**
 * 红黑榜：红榜=奖励/表彰，黑榜=警示。
 * 家长只看得到 relativeVisible 为真的黑榜记录（后端已过滤，这里只管画）。
 */
import { shortDate } from '@/utils/display'

defineProps({
  /** [{ id, title, red, labelName, updateTime }] */
  items: { type: Array, default: () => [] },
  isParent: { type: Boolean, default: false }
})
const emit = defineEmits(['open'])
</script>

<template>
  <ul class="space-y-1.5">
    <li v-for="r in items" :key="r.id">
      <button class="flex w-full items-center gap-2 py-1 text-left" @click="emit('open', r.id)">
        <span
          class="shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium"
          :class="r.red ? 'bg-rose-50 text-rose-600' : 'bg-slate-800 text-white'"
        >{{ r.red ? '红榜' : '黑榜' }}</span>
        <span class="min-w-0 flex-1 truncate text-sm">{{ r.title }}</span>
        <span v-if="r.labelName" class="hidden shrink-0 text-[11px] text-slate-400 sm:inline">
          {{ r.labelName }}
        </span>
        <span class="shrink-0 text-[11px] text-slate-400">{{ shortDate(r.updateTime) }}</span>
      </button>
    </li>
  </ul>
</template>
