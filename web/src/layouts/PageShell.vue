<!--
  二级页面外壳 PageShell（布局地基）

  为什么详情页也要套壳：PC 形态下如果没有统一外壳，每个页面就得自己画一个返回条，
  宽度、留白、返回行为很快就会各不一样。页面里只声明标题与返回目标，壳负责画。

  用法（router meta 里标 layout: 'page' 即可，页面模板里不写壳）：
    meta: { layout: 'page', title: '请假详情', back: '/leave' }

  - 桌面：TopNav + 一行「返回 + 标题 + 右侧操作」粘在 TopNav 下方
  - 移动：只有「返回 + 标题」这一条
-->
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopNav from '@/components/layout/TopNav.vue'
import PageContainer from '@/components/layout/PageContainer.vue'

defineProps({
  /** 容器宽度：default | narrow | form | wide */
  size: { type: String, default: 'default' }
})

const route = useRoute()
const router = useRouter()

const title = computed(() => route.meta.title || '')
const hint = computed(() => route.meta.hint || '')

function back() {
  const target = route.meta.back
  if (target) {
    router.push(target)
    return
  }
  // 有历史就退回上一页，直接打开链接的（没有历史）回首页
  if (window.history.length > 1) router.back()
  else router.push('/')
}
</script>

<template>
  <div class="min-h-screen">
    <TopNav />

    <header
      class="sticky top-0 z-20 border-b border-slate-200/70 bg-white/85 backdrop-blur lg:top-[60px]"
    >
      <div
        class="mx-auto flex h-14 max-w-content items-center gap-2 px-4 lg:h-[58px] lg:px-8"
      >
        <button
          type="button"
          class="-ml-1 grid h-9 w-9 shrink-0 place-items-center rounded-xl text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
          aria-label="返回"
          @click="back"
        >
          <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="m14.5 5-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>

        <div class="min-w-0 flex-1">
          <h1 class="truncate text-[15px] font-semibold leading-tight text-slate-900">
            {{ title }}
          </h1>
          <p v-if="hint" class="truncate text-[11px] leading-tight text-slate-400">{{ hint }}</p>
        </div>

        <slot name="actions" />
      </div>
    </header>

    <main>
      <PageContainer :size="size">
        <slot />
      </PageContainer>
    </main>
  </div>
</template>
