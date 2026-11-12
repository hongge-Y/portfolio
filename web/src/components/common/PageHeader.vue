<script setup>
/**
 * 二级页面统一头：返回 + 标题 + 右侧槽位。
 * 之前每个页各写一遍 header，六个"我的"页复制六次；抽出来统一规范。
 */
import { useRouter } from 'vue-router'

defineProps({
  title: { type: String, required: true },
  /** 返回地址；不传就 router.back() */
  back: { type: String, default: '' },
  /** 标题下方的小字说明 */
  hint: { type: String, default: '' }
})

const router = useRouter()
const emit = defineEmits(['back'])

function onBack(path) {
  emit('back')
  if (path) router.push(path)
  else router.back()
}
</script>

<template>
  <header class="sticky top-0 z-20 border-b border-slate-100 bg-white/95 backdrop-blur">
    <div class="mx-auto flex h-14 max-w-3xl items-center gap-2 px-4">
      <button class="-ml-1 grid h-8 w-8 place-items-center rounded-lg text-lg text-slate-400 transition hover:bg-slate-50" @click="onBack(back)">‹</button>
      <div class="min-w-0 flex-1">
        <p class="truncate text-base font-semibold leading-tight">{{ title }}</p>
        <p v-if="hint" class="truncate text-[11px] leading-tight text-slate-400">{{ hint }}</p>
      </div>
      <slot name="right" />
    </div>
  </header>
</template>
