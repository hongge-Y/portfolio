<script setup>
/**
 * 红黑榜列表（套 PageShell：标题与返回条由壳提供）
 *
 * 红/黑榜切换用 SegmentedTabs，列表用 usePagedList（竞态保护 + 统一三态）。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { contentApi } from '@/api/content'
import { usePagedList } from '@/composables/usePagedList'
import { useUserStore } from '@/stores/user'
import DataState from '@/components/common/DataState.vue'
import SegmentedTabs from '@/components/common/SegmentedTabs.vue'
import { shortDate } from '@/utils/display'

const router = useRouter()
const store = useUserStore()

const tab = ref('red')
const TABS = [
  { value: 'red', label: '红榜' },
  { value: 'black', label: '黑榜' }
]

const { items, total, status, error, load, refresh } = usePagedList(
  ({ pageNum, pageSize }) => contentApi.rankPortal(tab.value === 'red', pageNum, pageSize),
  { pageSize: 20 }
)

const emptyText = computed(() =>
  tab.value === 'red' ? '还没有红榜记录' : '没有对你公开的黑榜记录'
)

function switchTab(next) {
  tab.value = next
  void load({ reset: true })
}

function open(item) {
  router.push(`/rank/${item.id}`)
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center gap-3">
      <div class="min-w-0 flex-1">
        <p class="text-xs text-slate-400">
          {{ store.isParent ? '孩子的成长记录，仅显示可公开的部分' : '我的成长档案' }} · 共
          {{ total }} 条
        </p>
      </div>
      <SegmentedTabs :model-value="tab" :options="TABS" @change="switchTab" />
    </div>

    <DataState
      :status="status"
      :error-text="error"
      :empty-text="emptyText"
      icon="🏅"
      :rows="4"
      @retry="refresh"
    >
      <ul class="grid gap-3 md:grid-cols-2">
        <li v-for="r in items" :key="r.id">
          <article class="card-flat card-hover cursor-pointer p-4" @click="open(r)">
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0">
                <p class="truncate font-medium text-slate-900">{{ r.title }}</p>
                <p class="mt-0.5 line-clamp-2 text-xs leading-relaxed text-slate-500">
                  {{ r.subTitle || r.achievement || '—' }}
                </p>
              </div>
              <span
                v-if="r.labelName"
                class="shrink-0 rounded-md px-1.5 py-0.5 text-[10px]"
                :class="r.red ? 'bg-rose-50 text-rose-600' : 'bg-slate-800 text-white'"
              >
                {{ r.labelName }}
              </span>
              <span
                v-else
                class="shrink-0 rounded-md px-1.5 py-0.5 text-[10px]"
                :class="r.red ? 'bg-rose-50 text-rose-600' : 'bg-slate-100 text-slate-500'"
              >
                {{ r.red ? '红榜' : '黑榜' }}
              </span>
            </div>

            <div class="mt-3 flex items-center justify-between border-t border-slate-100 pt-2 text-[11px] text-slate-400">
              <span class="truncate">
                {{ r.employeeName || '—' }}
                <template v-if="r.employeeNo"> · {{ r.employeeNo }}</template>
              </span>
              <span class="shrink-0">{{ shortDate(r.updateTime) }}</span>
            </div>
          </article>
        </li>
      </ul>
    </DataState>
  </div>
</template>
