<!--
  服务状态卡（首页侧栏底部）

  把"演示环境"的事实写清楚：后端 / 数据库 / 表数 / JDK。
  答辩时不用再解释"这些数据是真的还是假的"。
-->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  health: { type: Object, default: null },
  termWeek: { type: Object, default: null }
})

const ok = computed(() => props.health?.status === 'UP')

const rows = computed(() => [
  { label: '后端服务', value: ok.value ? '正常' : '未连接', ok: ok.value },
  { label: '数据库', value: props.health?.db === 'UP' ? '正常' : '异常', ok: props.health?.db === 'UP' },
  { label: '库 · 表', value: props.health?.dbName ? `${props.health.dbName} · ${props.health.tables ?? '—'} 张` : '—' },
  { label: '运行环境', value: props.health?.java ? `JDK ${props.health.java}` : '—' },
  { label: '教学周', value: props.termWeek ? `第 ${props.termWeek.weekNo} 周` : '—' }
])
</script>

<template>
  <section class="card-flat p-4">
    <header class="mb-2 flex items-center justify-between">
      <h3 class="section-title">服务状态</h3>
      <span class="flex items-center gap-1.5 text-[11px]" :class="ok ? 'text-emerald-600' : 'text-rose-500'">
        <span class="h-1.5 w-1.5 rounded-full" :class="ok ? 'bg-emerald-500' : 'bg-rose-500'" />
        {{ ok ? '在线' : '离线' }}
      </span>
    </header>

    <dl class="space-y-1.5 text-[11px]">
      <div v-for="row in rows" :key="row.label" class="flex items-center justify-between gap-2">
        <dt class="shrink-0 text-slate-400">{{ row.label }}</dt>
        <dd
          class="min-w-0 truncate font-medium"
          :class="row.ok === false ? 'text-rose-500' : 'text-slate-600'"
        >
          {{ row.value }}
        </dd>
      </div>
    </dl>

    <p class="mt-2.5 border-t border-slate-100 pt-2 text-[10px] leading-relaxed text-slate-300">
      校园成长平台 V2.1 · Spring Boot 3 + MySQL 8 · AI 由 qwen-plus 提供。平台规则以学校正式通知为准。
    </p>
  </section>
</template>
