<script setup>
/**
 * 通用区块外壳：首页每个板块都是「卡片 + 标题 + 更多」这一个结构，
 * 抽出来之后各板块只关心自己的内容，标题栏样式只有一处需要维护。
 */
defineProps({
  title: { type: String, required: true },
  /** 标题右侧的小字（如 "第 9 教学周"） */
  hint: { type: String, default: '' },
  /** 右侧动作文字；不传则不显示 */
  moreText: { type: String, default: '' },
  /** 紧凑模式：内容区上下留白更小 */
  dense: { type: Boolean, default: false }
})

const emit = defineEmits(['more'])
</script>

<template>
  <section class="card min-w-0">
    <header class="mb-3 flex items-center justify-between gap-2">
      <div class="flex min-w-0 items-baseline gap-2">
        <h3 class="font-semibold">{{ title }}</h3>
        <span v-if="hint" class="truncate text-[11px] text-slate-400">{{ hint }}</span>
      </div>
      <button
        v-if="moreText"
        class="shrink-0 text-xs text-slate-400 transition hover:text-brand-600"
        @click="emit('more')"
      >
        {{ moreText }} ›
      </button>
    </header>
    <div :class="dense ? '' : 'min-h-[64px]'">
      <slot />
    </div>
  </section>
</template>
