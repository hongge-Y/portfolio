<script setup>
/**
 * AI 快捷提问卡：把"刚登录不知道该干什么"的学生直接推进 AI 对话。
 * 点 chip 时把问题带进抽屉并自动发送（走 useAiPanel.ask）。
 */
const emit = defineEmits(['ask'])

const QUESTIONS = ['请假流程是怎样的？', '奖学金怎么算分？', '这周还有什么课？']
</script>

<template>
  <section class="relative overflow-hidden rounded-2xl border border-brand-100 bg-gradient-to-br from-brand-50/90 to-white p-4">
    <span class="pointer-events-none absolute -right-6 -top-8 h-24 w-24 rounded-full bg-brand-500/10" />
    <div class="relative flex items-start gap-3">
      <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-brand-500 text-sm font-bold text-white">AI</span>
      <div class="min-w-0 flex-1">
        <p class="text-sm font-semibold">有不清楚的，直接问 AI 助手</p>
        <p class="mt-0.5 text-[11px] text-slate-500">
          它读过平台的规则和你的数据：请假要几级审批、奖学金怎么计分、课表在哪看
        </p>
        <div class="mt-2 flex flex-wrap gap-1.5">
          <button
            v-for="q in QUESTIONS"
            :key="q"
            class="rounded-full border border-brand-100 bg-white px-2.5 py-1 text-[11px] text-brand-700 transition hover:border-brand-500 hover:bg-brand-500 hover:text-white"
            @click="emit('ask', q)"
          >
            {{ q }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
