<!--
  分段切换（收件箱/发件箱、红榜/黑榜、我的/班级）

  比两个大按钮更省视觉重量；数量角标可传 count。
-->
<script setup>
defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, required: true }
})

const emit = defineEmits(['update:modelValue', 'change'])

function pick(value) {
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<template>
  <div class="seg" role="tablist">
    <button
      v-for="opt in options"
      :key="String(opt.value)"
      type="button"
      role="tab"
      class="seg-item"
      :class="modelValue === opt.value ? 'seg-item-on' : ''"
      :aria-selected="modelValue === opt.value"
      @click="pick(opt.value)"
    >
      {{ opt.label }}
      <span v-if="opt.count !== undefined" class="ml-1 text-[11px] text-slate-400">{{ opt.count }}</span>
    </button>
  </div>
</template>
