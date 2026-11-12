<script setup>
/**
 * 功能入口网格。入口不是写死的模板，而是由 Home 按角色算出来的数组传进来，
 * 所以同一个组件能吃下"学生 / 家长 / 辅导员 / 管理员"四套菜单。
 *
 * entries: [{ key, title, desc, icon, to?, highlight?, badge? }]
 */
defineProps({
  entries: { type: Array, default: () => [] },
  columns: { type: Number, default: 4 }
})

const emit = defineEmits(['select'])
</script>

<template>
  <section
    class="grid gap-2.5"
    :class="columns === 4 ? 'grid-cols-2 sm:grid-cols-4' : 'grid-cols-3'"
  >
    <button
      v-for="e in entries"
      :key="e.key"
      class="group relative flex min-w-0 flex-col items-start rounded-2xl border bg-white p-3 text-left shadow-card transition hover:-translate-y-0.5 hover:shadow-lg active:translate-y-0"
      :class="e.highlight ? 'border-brand-100 bg-gradient-to-br from-brand-50/90 to-white' : 'border-slate-100'"
      @click="emit('select', e)"
    >
      <span
        class="grid h-9 w-9 place-items-center rounded-xl text-lg"
        :class="e.highlight ? 'bg-brand-500/10' : 'bg-slate-50'"
      >{{ e.icon }}</span>
      <span class="mt-2 w-full truncate text-[13px] font-medium leading-tight">{{ e.title }}</span>
      <span class="mt-0.5 w-full truncate text-[11px] text-slate-400">{{ e.desc }}</span>

      <span
        v-if="e.badge"
        class="absolute right-2 top-2 grid h-4 min-w-[16px] place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-medium text-white"
      >{{ e.badge > 99 ? '99+' : e.badge }}</span>

      <span
        v-if="e.highlight"
        class="pointer-events-none absolute bottom-2 right-2 text-[10px] font-medium text-brand-500 opacity-0 transition group-hover:opacity-100"
      >AI ›</span>
    </button>
  </section>
</template>
