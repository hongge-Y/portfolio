<script setup>
/** 赛事风云：报名状态 + 比赛时间 + 组队人数 */
import { daysLeft, shortDate } from '@/utils/display'

defineProps({
  /** [{ id, title, matchTime, groupCount, canRegister, registered, endRegisterTime }] */
  items: { type: Array, default: () => [] }
})
const emit = defineEmits(['open'])

function registerHint(m) {
  if (m.registered) return '已报名'
  const left = daysLeft(m.endRegisterTime)
  if (!m.canRegister) return '报名已截止'
  if (left !== null && left >= 0) return left === 0 ? '今天截止' : `还剩 ${left} 天`
  return '报名中'
}
</script>

<template>
  <ul class="space-y-2">
    <li v-for="m in items" :key="m.id">
      <button
        class="w-full rounded-xl border border-slate-100 p-3 text-left transition hover:border-brand-100 hover:bg-brand-50/40"
        @click="emit('open', m.id)"
      >
        <div class="flex items-center justify-between gap-2">
          <span class="min-w-0 flex-1 truncate text-sm font-medium">{{ m.title }}</span>
          <span
            class="shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium"
            :class="
              m.registered
                ? 'bg-brand-50 text-brand-700'
                : m.canRegister
                  ? 'bg-emerald-50 text-emerald-700'
                  : 'bg-slate-100 text-slate-500'
            "
          >{{ m.registered ? '已报名' : m.canRegister ? '报名中' : '已截止' }}</span>
        </div>
        <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[11px] text-slate-400">
          <span>比赛 {{ shortDate(m.matchTime) }}</span>
          <span v-if="m.groupCount">· 组队 {{ m.groupCount }} 人</span>
          <span v-if="m.canRegister" class="text-amber-600">· {{ registerHint(m) }}</span>
        </p>
      </button>
    </li>
  </ul>
</template>
