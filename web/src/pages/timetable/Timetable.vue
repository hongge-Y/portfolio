<script setup>
/**
 * 课表（套 PageShell，size=wide）
 *
 * 桌面：7 天 × 节次的周视图网格（课程块按 sectionStart/sectionEnd 跨行）；
 * 移动：按天分组的卡片，避免横向挤压。
 * 数据全来自 /v1/edu/timetable，系统不生成假课表。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { eduApi } from '@/api/edu'
import DataState from '@/components/common/DataState.vue'
import { SECTION_TIMES } from '@/features/timetable/sections'
import { humanMessage } from '@/utils/crypto'

const router = useRouter()

const DAY_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const term = ref('')
const weekNo = ref(1)
const maxWeek = ref(20)
const items = ref([])
const emptyReason = ref('')
const status = ref('loading')
const error = ref('')

const todayWeekDay = computed(() => new Date().getDay() || 7)

/** 需要的行数：至少 12 节，课表更长时自动扩 */
const sectionCount = computed(() => {
  const max = items.value.reduce((m, c) => Math.max(m, c.sectionEnd || 0), 0)
  return Math.max(12, max)
})

const gridStyle = computed(() => ({
  gridTemplateColumns: '52px repeat(7, minmax(0, 1fr))',
  gridAutoRows: '56px'
}))

/** 按天分组（移动端用），只保留有课的天 */
const dayGroups = computed(() => {
  const map = {}
  items.value.forEach((c) => {
    ;(map[c.weekDay] ||= []).push(c)
  })
  Object.values(map).forEach((arr) => arr.sort((a, b) => a.sectionStart - b.sectionStart))
  return Object.keys(map)
    .map(Number)
    .sort((a, b) => a - b)
    .map((d) => ({ day: d, name: DAY_NAMES[d - 1], items: map[d] }))
})

function courseStyle(c) {
  const span = Math.max(1, (c.sectionEnd || c.sectionStart) - c.sectionStart + 1)
  return {
    gridColumn: `${(c.weekDay || 1) + 1}`,
    gridRow: `${c.sectionStart + 1} / span ${span}`,
    backgroundColor: c.color || '#4f7cff'
  }
}

async function load() {
  status.value = 'loading'
  error.value = ''
  try {
    const res = await eduApi.timetable(term.value || undefined, weekNo.value)
    term.value = res.term
    weekNo.value = res.weekNo
    maxWeek.value = res.maxWeek || 20
    items.value = res.items || []
    emptyReason.value = res.emptyReason || ''
    status.value = items.value.length ? 'ready' : 'empty'
  } catch (e) {
    items.value = []
    error.value = humanMessage(e?.message, '课表加载失败')
    status.value = 'error'
  }
}

async function init() {
  status.value = 'loading'
  try {
    const t = await eduApi.term()
    term.value = t.term
    weekNo.value = t.weekNo
    maxWeek.value = t.maxWeek || 20
  } catch {
    /* 学期接口失败时退回默认周次，load() 会再报一次真正的错 */
  }
  await load()
}

function changeWeek(delta) {
  const next = weekNo.value + delta
  if (next < 1 || next > maxWeek.value) return
  weekNo.value = next
  void load()
}

onMounted(init)
</script>

<template>
  <div class="space-y-4">
    <!-- 工具条 -->
    <div class="flex flex-wrap items-center gap-3">
      <div class="seg">
        <button type="button" class="grid h-8 w-8 place-items-center rounded-lg text-slate-500 hover:bg-white hover:text-slate-900" aria-label="上一周" @click="changeWeek(-1)">
          ‹
        </button>
        <span class="px-2 text-[13px] font-medium text-slate-900">第 {{ weekNo }} 周</span>
        <button type="button" class="grid h-8 w-8 place-items-center rounded-lg text-slate-500 hover:bg-white hover:text-slate-900" aria-label="下一周" @click="changeWeek(1)">
          ›
        </button>
      </div>

      <p class="min-w-0 flex-1 truncate text-xs text-slate-400">
        {{ term || '—' }}
        <template v-if="items.length"> · 本周 {{ items.length }} 门课</template>
      </p>

      <button type="button" class="btn-ghost-sm" @click="router.push('/timetable/eval')">课程评价</button>
      <button type="button" class="btn-ghost-sm" @click="load">刷新</button>
    </div>

    <DataState
      :status="status"
      :error-text="error"
      :empty-text="emptyReason || '本周没有排课'"
      icon="📅"
      :rows="5"
      @retry="load"
    >
      <!-- 桌面：周视图网格 -->
      <div class="card-flat hidden overflow-hidden lg:block">
        <div class="grid" :style="gridStyle">
          <!-- 表头 -->
          <div class="border-b border-slate-100 bg-slate-50/70 py-2 text-center text-[11px] text-slate-400">
            节次
          </div>
          <div
            v-for="d in 7"
            :key="`h-${d}`"
            class="border-b border-l border-slate-100 bg-slate-50/70 py-2 text-center text-[12px] font-medium"
            :class="d === todayWeekDay ? 'text-brand-600' : 'text-slate-600'"
          >
            {{ DAY_NAMES[d - 1] }}
            <span v-if="d === todayWeekDay" class="ml-1 rounded bg-brand-50 px-1 text-[10px] text-brand-600">今天</span>
          </div>

          <!-- 背景格 + 节次标签：全部显式定位，否则会被"显式放置"的课程块挤到别的列 -->
          <template v-for="s in sectionCount" :key="`r-${s}`">
            <div
              class="flex flex-col items-center justify-center border-t border-slate-100 text-[10px] leading-tight text-slate-400"
              :style="{ gridColumn: 1, gridRow: s + 1 }"
            >
              <span class="font-medium text-slate-500">{{ s }}</span>
              <span>{{ SECTION_TIMES[s]?.start || '' }}</span>
            </div>
            <div
              v-for="d in 7"
              :key="`c-${s}-${d}`"
              class="border-l border-t border-slate-100"
              :class="d === todayWeekDay ? 'bg-brand-50/30' : ''"
              :style="{ gridColumn: d + 1, gridRow: s + 1 }"
            />
          </template>

          <!-- 课程块 -->
          <button
            v-for="c in items"
            :key="c.id"
            type="button"
            class="relative m-1 overflow-hidden rounded-lg px-2 py-1.5 text-left text-white transition hover:brightness-105"
            :style="courseStyle(c)"
            :title="`${c.courseName} · ${c.teacherName || ''} ${c.location || ''}`"
          >
            <span class="block truncate text-[12px] font-medium leading-tight">{{ c.courseName }}</span>
            <span class="mt-0.5 block truncate text-[10px] opacity-90">
              第{{ c.sectionStart }}-{{ c.sectionEnd }}节
            </span>
            <span class="mt-0.5 block truncate text-[10px] opacity-85">
              {{ c.location || '—' }}<template v-if="c.teacherName"> · {{ c.teacherName }}</template>
            </span>
          </button>
        </div>
      </div>

      <!-- 移动：按天分组 -->
      <div class="space-y-3 lg:hidden">
        <section v-for="g in dayGroups" :key="g.day" class="card-flat overflow-hidden">
          <header
            class="flex items-center justify-between border-b border-slate-100 px-4 py-2"
            :class="g.day === todayWeekDay ? 'bg-brand-50/60' : 'bg-slate-50/60'"
          >
            <p class="text-[13px] font-medium text-slate-700">{{ g.name }}</p>
            <p class="text-[11px] text-slate-400">{{ g.items.length }} 门课</p>
          </header>
          <ul class="divide-y divide-slate-100">
            <li v-for="c in g.items" :key="c.id" class="flex gap-3 px-4 py-2.5">
              <span class="mt-0.5 h-full w-1 shrink-0 rounded-full" :style="{ backgroundColor: c.color || '#4f7cff' }" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-slate-800">{{ c.courseName }}</p>
                <p class="mt-0.5 truncate text-[11px] text-slate-400">
                  第 {{ c.sectionStart }}-{{ c.sectionEnd }} 节 · {{ SECTION_TIMES[c.sectionStart]?.start || '' }}
                  <template v-if="c.location"> · {{ c.location }}</template>
                  <template v-if="c.teacherName"> · {{ c.teacherName }}</template>
                </p>
              </div>
            </li>
          </ul>
        </section>
      </div>
    </DataState>

    <p class="text-center text-[11px] text-slate-300">
      课表由教务导入（管理后台 → 课表导入），本系统不生成假数据
    </p>
  </div>
</template>
