<script setup>
/**
 * 404 —— 以前是静默 redirect 回首页，用户点错链接会以为"页面自己跳走了"。
 * 现在明说找不到，并给两条出路。
 */
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="flex flex-col items-center justify-center py-16 text-center lg:py-24">
    <p class="text-6xl font-black tracking-tight text-slate-200">404</p>
    <h2 class="mt-4 text-lg font-semibold text-slate-900">这个页面不在成长档案里</h2>
    <p class="mt-2 max-w-xs text-sm leading-relaxed text-slate-400">
      链接可能已经失效，或者你没有访问它的身份。可以回首页，也可以看看站内消息。
    </p>
    <div class="mt-6 flex gap-2.5">
      <button type="button" class="btn-solid" @click="router.push('/')">回到首页</button>
      <button type="button" class="btn-ghost" @click="router.push('/message')">查看消息</button>
    </div>
  </div>
</template>
