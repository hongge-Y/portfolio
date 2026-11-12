<script setup>
/**
 * 首页主视觉卡片：问候 + 日期 + 若干可点指标 + 下一节课。
 *
 * 指标不是写死的三个 —— 学生关心"今日课程"、家长关心"孩子记录"、
 * 审批人关心"待审批"，所以由调用方传 metrics 数组进来，这里只负责画。
 */
import { computed } from 'vue'
import { fullDateLabel, greeting, weekdayLabel } from '@/utils/display'
import { roleLabels } from '@/features/account/roles'

const props = defineProps({
  name: { type: String, default: '同学' },
  employeeNo: { type: String, default: '' },
  roles: { type: Array, default: () => [] },
  /** [{ label, value, to, highlight }] */
  metrics: { type: Array, default: () => [] },
  /** 下一节课 { sectionStart, sectionEnd, courseName, location } */
  nextCourse: { type: Object, default: null },
  /** { term, weekNo } */
  termWeek: { type: Object, default: null },
  /** 今天共有几节课：用来区分"没排课"和"今天的课已结束" */
  todayCount: { type: Number, default: 0 },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['go'])

const hello = computed(() => `${greeting()}，${props.name}`)
const roleText = computed(() => (props.roles.length ? roleLabels(props.roles).join(' / ') : '访客'))
const isParent = computed(() => props.roles.includes('PARENT'))
</script>

<template>
  <section
    class="relative overflow-hidden rounded-2xl bg-gradient-to-br from-brand-500 via-brand-600 to-indigo-500 p-5 text-white shadow-lg shadow-brand-500/20"
  >
    <!-- 装饰光斑：跟着卡片裁掉，不参与布局 -->
    <span class="pointer-events-none absolute -right-10 -top-12 h-40 w-40 rounded-full bg-white/10" />
    <span class="pointer-events-none absolute -bottom-16 right-16 h-32 w-32 rounded-full bg-white/5" />

    <div class="relative flex items-start justify-between gap-3">
      <div class="min-w-0">
        <h1 class="truncate text-xl font-bold leading-tight">{{ hello }}</h1>
        <p class="mt-1 truncate text-xs text-white/80">学号 {{ employeeNo || '—' }} · {{ roleText }}</p>

        <p
          v-if="nextCourse"
          class="mt-3 inline-flex max-w-full items-center gap-1.5 rounded-lg bg-white/15 px-2.5 py-1 text-[11px]"
        >
          <span class="shrink-0">下一节</span>
          <span class="truncate">
            第 {{ nextCourse.sectionStart }}-{{ nextCourse.sectionEnd }} 节 · {{ nextCourse.courseName }}
            <template v-if="nextCourse.location"> @ {{ nextCourse.location }}</template>
          </span>
        </p>
        <p
          v-else-if="!loading"
          class="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-white/10 px-2.5 py-1 text-[11px] text-white/70"
        >
          <template v-if="todayCount > 0">今天的课已结束，可以安排复习</template>
          <template v-else>{{ isParent ? '今天没有需要关注的提醒' : '今天没有排课，可以安排自习或看看红黑榜' }}</template>
        </p>
      </div>

      <div class="shrink-0 text-right">
        <p class="text-[11px] text-white/70">{{ weekdayLabel() }}</p>
        <p class="mt-0.5 text-sm font-semibold">{{ fullDateLabel() }}</p>
        <p v-if="termWeek" class="mt-1 text-[11px] text-white/70">第 {{ termWeek.weekNo }} 教学周</p>
      </div>
    </div>

    <!-- 指标：只放"点进去能做事"的数字 -->
    <div
      v-if="metrics.length"
      class="relative mt-4 grid gap-2"
      :style="{ gridTemplateColumns: `repeat(${metrics.length}, minmax(0, 1fr))` }"
    >
      <button
        v-for="m in metrics"
        :key="m.label"
        class="rounded-xl px-3 py-2 text-left transition"
        :class="m.highlight ? 'bg-amber-300/90 text-slate-900 hover:bg-amber-300' : 'bg-white/15 hover:bg-white/25'"
        @click="emit('go', m.to)"
      >
        <p class="text-lg font-bold leading-none">{{ m.value }}</p>
        <p class="mt-1 text-[11px]" :class="m.highlight ? 'text-slate-700' : 'text-white/75'">{{ m.label }}</p>
      </button>
    </div>
  </section>
</template>
