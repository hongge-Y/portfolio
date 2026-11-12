<!--
  筛选 chip 组（列表页通用）

  用法：
    <FilterChips v-model="status" :options="LEAVE_STATUS_CHIPS" @change="load({ reset: true })" />

  选项数组写成 [{ value, label }]，value 为 '' 表示"全部"。
-->
<script setup>
defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, required: true },
  /** 小屏下横向滚动，避免换行把筛选区撑高 */
  scrollable: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'change'])

function pick(value) {
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<template>
  <div class="flex gap-2 pb-1" :class="scrollable ? 'overflow-x-auto' : 'flex-wrap'">
    <button
      v-for="opt in options"
      :key="String(opt.value)"
      type="button"
      class="chip"
      :class="modelValue === opt.value ? 'chip-on' : 'chip-off'"
      @click="pick(opt.value)"
    >
      {{ opt.label }}
      <span v-if="opt.count !== undefined" class="ml-1 opacity-70">{{ opt.count }}</span>
    </button>
  </div>
</template>
