<script setup>
/**
 * 三态占位：加载 / 失败 / 空 / 正常（slot）。
 *
 * 之前每个板块各写一遍 v-if loading + v-else-if empty，
 * 统一到这里之后，每个板块只需要给一个 status，内容照常写在默认插槽里。
 */
defineProps({
  /** loading | error | empty | ready */
  status: { type: String, default: 'ready' },
  emptyText: { type: String, default: '暂无数据' },
  errorText: { type: String, default: '加载失败' },
  /** 加载骨架条数 */
  rows: { type: Number, default: 3 },
  /** 空状态图标（emoji） */
  icon: { type: String, default: '🗂️' }
})

const emit = defineEmits(['retry'])
</script>

<template>
  <!-- 骨架屏：宽度错开一点，比三条等宽灰条更像真实内容 -->
  <div v-if="status === 'loading'" class="space-y-2 py-1">
    <div
      v-for="i in rows"
      :key="i"
      class="h-4 animate-pulse rounded bg-slate-100"
      :style="{ width: `${100 - (i % 3) * 12}%` }"
    />
  </div>

  <div v-else-if="status === 'error'" class="py-4 text-center">
    <p class="text-sm text-rose-500">{{ errorText }}</p>
    <button class="mt-2 text-xs text-brand-600 underline" @click="emit('retry')">重试</button>
  </div>

  <div v-else-if="status === 'empty'" class="py-6 text-center">
    <div class="text-2xl opacity-60">{{ icon }}</div>
    <p class="mt-2 text-sm text-slate-400">{{ emptyText }}</p>
    <slot name="empty-extra" />
  </div>

  <slot v-else />
</template>
