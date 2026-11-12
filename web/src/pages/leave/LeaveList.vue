<script setup>
/**
 * 请假列表（套 PageShell）
 *
 * 学委/审批人多一个「班级请假」tab（不含理由与附件 —— 字段缺失就不渲染，不写"暂无"）。
 * 状态筛选 chips、状态文案与徽标配色都来自 features/leave/status.js。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { leaveApi } from '@/api/leave'
import { useUserStore } from '@/stores/user'
import { usePagedList } from '@/composables/usePagedList'
import { useConfirm } from '@/composables/useConfirm'
import {
  LEAVE_STATUS_CHIPS,
  canCancel,
  canReturn,
  leaveStatusClass,
  leaveStatusText
} from '@/features/leave/status'
import DataState from '@/components/common/DataState.vue'
import SegmentedTabs from '@/components/common/SegmentedTabs.vue'
import FilterChips from '@/components/common/FilterChips.vue'
import { toast } from '@/utils/toast'

const router = useRouter()
const store = useUserStore()
const { confirm } = useConfirm()

const tab = ref('mine')
const status = ref('')

const canSeeClass = computed(() => store.isMonitor || store.hasPerm('leave:approve'))
const tabs = computed(() => [
  { value: 'mine', label: '我的请假' },
  ...(canSeeClass.value ? [{ value: 'class', label: '班级请假' }] : [])
])

const { items, total, pageNum, totalPages, hasPrev, hasNext, status: listStatus, error, load, refresh, next, prev } =
  usePagedList(
    ({ pageNum: no, pageSize }) => {
      const body = {
        pageNum: no,
        pageSize,
        searchModel: status.value === '' ? {} : { status: status.value }
      }
      return tab.value === 'mine' ? leaveApi.personal(body) : leaveApi.classPage(body)
    },
    { pageSize: 10 }
  )

function switchTab(next) {
  tab.value = next
  void load({ reset: true })
}

function onStatus() {
  void load({ reset: true })
}

async function doCancel(item) {
  const ok = await confirm({
    title: '取消请假',
    content: '取消后这条假条结束，需要重新提交申请。确定取消吗？',
    confirmText: '取消请假',
    tone: 'danger'
  })
  if (!ok) return
  await leaveApi.cancel(item.id)
  toast('已取消')
  await refresh()
}

async function doReturn(item) {
  const ok = await confirm({
    title: '销假',
    content: '确认已经返校并销假？销假后该假条结束。',
    confirmText: '确认销假'
  })
  if (!ok) return
  await leaveApi.returnLeave(item.id)
  toast('销假成功')
  await refresh()
}
</script>

<template>
  <div class="space-y-4">
    <!-- 工具条 -->
    <div class="flex flex-wrap items-center gap-3">
      <SegmentedTabs v-if="tabs.length > 1" :model-value="tab" :options="tabs" @change="switchTab" />
      <p class="min-w-0 flex-1 text-xs text-slate-400">共 {{ total }} 条</p>
      <button
        v-if="tab === 'mine'"
        type="button"
        class="btn-solid hidden lg:inline-flex"
        @click="router.push('/leave/new')"
      >
        + 发起请假
      </button>
    </div>

    <FilterChips v-model="status" :options="LEAVE_STATUS_CHIPS" @change="onStatus" />

    <p v-if="tab === 'class'" class="rounded-xl bg-slate-50 px-3 py-2 text-[11px] text-slate-500">
      学委视角：仅展示已批准的班级请假，不含请假理由与附件。
    </p>

    <DataState
      :status="listStatus"
      :error-text="error"
      empty-text="暂无请假记录"
      icon="🗓️"
      :rows="4"
      @retry="refresh"
    >
      <template #empty-extra>
        <button
          v-if="tab === 'mine'"
          type="button"
          class="btn-solid mt-3"
          @click="router.push('/leave/new')"
        >
          + 发起请假
        </button>
      </template>
      <ul class="space-y-3">
        <li v-for="item in items" :key="item.id">
          <article class="card-flat card-hover cursor-pointer p-4" @click="router.push(`/leave/${item.id}`)">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-600">
                    {{ item.typeText }}
                  </span>
                  <span v-if="tab === 'class'" class="truncate text-sm font-medium text-slate-900">
                    {{ item.employeeName }} · {{ item.employeeNo }}
                  </span>
                  <span v-else class="truncate text-[11px] text-slate-400">{{ item.className }}</span>
                </div>

                <p class="mt-2 text-sm font-medium text-slate-800">
                  {{ (item.startTime || '').slice(0, 16) }} → {{ (item.endTime || '').slice(0, 16) }}
                </p>
                <p class="mt-0.5 text-xs text-slate-400">
                  {{ item.days }} 天<template v-if="tab === 'mine' && item.reason">
                    · {{ item.reason }}</template
                  >
                </p>
              </div>

              <span
                class="shrink-0 rounded-md px-2 py-1 text-xs font-medium"
                :class="leaveStatusClass(item.status)"
              >
                {{ leaveStatusText(item.status, item.statusText) }}
              </span>
            </div>

            <div
              v-if="tab === 'mine' && (canCancel(item.status) || canReturn(item.status))"
              class="mt-3 flex justify-end gap-2 border-t border-slate-100 pt-3"
            >
              <button
                v-if="canCancel(item.status)"
                type="button"
                class="btn-ghost-sm"
                @click.stop="doCancel(item)"
              >
                取消
              </button>
              <button v-if="canReturn(item.status)" type="button" class="btn-sm bg-brand-500 text-white hover:bg-brand-600" @click.stop="doReturn(item)">
                销假
              </button>
            </div>
          </article>
        </li>
      </ul>
    </DataState>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="flex items-center justify-center gap-3 pt-1">
      <button type="button" class="btn-ghost-sm" :disabled="!hasPrev" @click="prev">上一页</button>
      <span class="text-xs text-slate-400">{{ pageNum }} / {{ totalPages }}</span>
      <button type="button" class="btn-ghost-sm" :disabled="!hasNext" @click="next">下一页</button>
    </div>

    <!-- 移动端悬浮新增 -->
    <button
      v-if="tab === 'mine'"
      type="button"
      class="fixed bottom-[calc(80px+env(safe-area-inset-bottom))] right-5 z-20 grid h-14 w-14 place-items-center rounded-full bg-brand-500 text-2xl leading-none text-white shadow-lg shadow-brand-500/30 transition active:scale-95 lg:hidden"
      aria-label="发起请假"
      @click="router.push('/leave/new')"
    >
      +
    </button>
  </div>
</template>
