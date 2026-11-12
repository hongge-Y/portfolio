<script setup>
/**
 * 消息（一级页面，套 AppShell）
 *
 * 结构：桌面两列卡片流、移动单列；收件箱/发件箱用 SegmentedTabs，
 * 筛选走 FilterChips + 发件人下拉。列表状态交给 usePagedList。
 *
 * 已读红点：进入收件箱后刷新一次 app.unread（外壳的角标同一数据源）。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { messageApi } from '@/api/message'
import { levelBarClass, levelClass, levelText, READ_STATUS_CHIPS } from '@/features/message/level'
import { usePagedList } from '@/composables/usePagedList'
import { useAppStore } from '@/stores/app'
import DataState from '@/components/common/DataState.vue'
import SegmentedTabs from '@/components/common/SegmentedTabs.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import { friendlyTime } from '@/utils/display'

const router = useRouter()
const app = useAppStore()

const tab = ref('inbox')
const fromId = ref('')
const readStatus = ref('')
const senders = ref([])

const TABS = [
  { value: 'inbox', label: '收件箱' },
  { value: 'sent', label: '发件箱' }
]

const { items, total, status, error, load, refresh } = usePagedList(
  ({ pageNum, pageSize }) => {
    const searchModel = {}
    if (tab.value === 'inbox' && fromId.value !== '') searchModel.fromId = fromId.value
    if (readStatus.value !== '') searchModel.readStatus = readStatus.value
    const body = { pageNum, pageSize, searchModel }
    return tab.value === 'inbox' ? messageApi.inbox(body) : messageApi.sent(body)
  },
  { pageSize: 50 }
)

const unreadInPage = computed(
  () => items.value.filter((m) => m.readStatus === 0 && tab.value === 'inbox').length
)

watch(status, (v) => {
  if (v === 'ready' && tab.value === 'inbox') void app.refreshUnread()
})

function switchTab(next) {
  tab.value = next
  fromId.value = ''
  readStatus.value = ''
  void load({ reset: true })
}

function onFilter() {
  void load({ reset: true })
}

function open(item) {
  router.push(`/message/${item.id}?box=${tab.value}`)
}

onMounted(async () => {
  senders.value = await messageApi.senders().catch(() => [])
})
</script>

<template>
  <div class="space-y-4">
    <!-- 页头：标题 + 切换 + 计数 -->
    <div class="flex flex-wrap items-center gap-3">
      <div class="min-w-0 flex-1">
        <h2 class="text-lg font-semibold tracking-tight text-slate-900">消息中心</h2>
        <p class="mt-0.5 text-xs text-slate-400">
          共 {{ total }} 条<template v-if="tab === 'inbox' && unreadInPage">
            · 本页 {{ unreadInPage }} 条未读</template
          >
        </p>
      </div>
      <SegmentedTabs :model-value="tab" :options="TABS" @change="switchTab" />
    </div>

    <!-- 筛选 -->
    <div class="flex flex-wrap items-center gap-2">
      <FilterChips
        v-if="tab === 'inbox'"
        v-model="readStatus"
        :options="READ_STATUS_CHIPS"
        :scrollable="false"
        @change="onFilter"
      />
      <select
        v-if="tab === 'inbox'"
        v-model="fromId"
        class="h-8 rounded-lg border border-slate-200 bg-white px-2 text-xs text-slate-600 outline-none focus:border-brand-500"
        @change="onFilter"
      >
        <option value="">全部发件人</option>
        <option v-for="s in senders" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
    </div>

    <!-- 列表 -->
    <DataState
      :status="status"
      :error-text="error"
      empty-text="这里还没有消息"
      icon="📭"
      :rows="4"
      @retry="refresh"
    >
      <ul class="grid gap-3 md:grid-cols-2">
        <li v-for="m in items" :key="m.id">
          <article
            class="card-flat card-hover relative cursor-pointer overflow-hidden p-4"
            @click="open(m)"
          >
            <!-- 未读左侧色条：比只加粗更容易扫 -->
            <span
              v-if="m.readStatus === 0 && tab === 'inbox'"
              class="absolute inset-y-0 left-0 w-1"
              :class="levelBarClass(m.level)"
            />
            <div class="flex items-start gap-2.5">
              <span
                class="shrink-0 rounded px-1.5 py-0.5 text-[10px]"
                :class="levelClass(m.level)"
              >
                {{ levelText(m.level) }}
              </span>
              <div class="min-w-0 flex-1">
                <p
                  class="truncate text-[14px] leading-snug"
                  :class="
                    m.readStatus === 0 && tab === 'inbox'
                      ? 'font-semibold text-slate-900'
                      : 'text-slate-600'
                  "
                >
                  {{ m.title }}
                </p>
                <p class="mt-1 flex items-center gap-1.5 truncate text-[11px] text-slate-400">
                  <span v-if="tab === 'inbox'" class="truncate">{{ m.fromName || '系统' }}</span>
                  <span>{{ friendlyTime(m.createTime || m.sendTime) }}</span>
                </p>
              </div>
              <span
                v-if="m.readStatus === 0 && tab === 'inbox'"
                class="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-rose-500"
                aria-label="未读"
              />
            </div>
          </article>
        </li>
      </ul>
    </DataState>
  </div>
</template>
