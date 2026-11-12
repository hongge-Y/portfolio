<script setup>
/**
 * 奖学金（套 PageShell）
 *
 * 四个视角按身份出现：可申请 / 我的申请 / 待审批（辅导员·书记·管理员）/ 公示。
 * 二次确认走 useConfirm，驳回原因用行内输入框（不再用浏览器 prompt）。
 */
import { computed, reactive, ref } from 'vue'
import { scholarshipApi } from '@/api/scholarship'
import { useUserStore } from '@/stores/user'
import { useConfirm } from '@/composables/useConfirm'
import DataState from '@/components/common/DataState.vue'
import SegmentedTabs from '@/components/common/SegmentedTabs.vue'
import { toast } from '@/utils/toast'
import { humanMessage } from '@/utils/crypto'

const store = useUserStore()
const { confirm } = useConfirm()

const tab = ref('plans')
const plans = ref([])
const mine = ref([])
const pending = ref([])
const publicity = ref(null)
const status = ref('loading')
const error = ref('')

const activePlan = ref(null)
const form = reactive({ gpa: 3.5, statement: '' })
const submitting = ref(false)
/** 驳回原因：{ [applyId]: text } */
const rejectReasons = reactive({})

const canApprove = computed(() =>
  store.roles.some((r) => ['COUNSELOR', 'SECRETARY', 'ADMIN'].includes(r))
)

const tabs = computed(() => [
  { value: 'plans', label: '可申请' },
  { value: 'mine', label: '我的申请' },
  ...(canApprove.value ? [{ value: 'approve', label: '待审批', count: pending.value.length || undefined }] : []),
  { value: 'publicity', label: '公示' }
])

function applyStatusClass(s) {
  if ([40, 70].includes(s)) return 'bg-emerald-50 text-emerald-700'
  if (s === 50) return 'bg-rose-50 text-rose-600'
  if (s === 60) return 'bg-brand-50 text-brand-700'
  return 'bg-amber-50 text-amber-700'
}

async function loadAll() {
  status.value = 'loading'
  error.value = ''
  try {
    const [p, m, a, pub] = await Promise.allSettled([
      scholarshipApi.plans(),
      scholarshipApi.myApplies(),
      canApprove.value ? scholarshipApi.approvePage() : Promise.resolve({ list: [] }),
      scholarshipApi.publicity()
    ])
    plans.value = p.status === 'fulfilled' ? p.value || [] : []
    mine.value = m.status === 'fulfilled' ? m.value?.list || [] : []
    pending.value = a.status === 'fulfilled' ? a.value?.list || [] : []
    publicity.value = pub.status === 'fulfilled' ? pub.value : null
    status.value = 'ready'
  } catch (e) {
    error.value = humanMessage(e?.message, '奖学金数据加载失败')
    status.value = 'error'
  }
}

function openApply(p) {
  activePlan.value = p
  form.gpa = 3.5
  form.statement = ''
}

async function submitApply() {
  if (!activePlan.value) return
  submitting.value = true
  try {
    const res = await scholarshipApi.apply({
      planId: activePlan.value.id,
      gpa: Number(form.gpa),
      statement: form.statement
    })
    toast(`提交成功，综合分 ${res.totalScore}`)
    activePlan.value = null
    await loadAll()
    tab.value = 'mine'
  } catch (e) {
    toast(humanMessage(e?.message, '提交失败'), 'error')
  } finally {
    submitting.value = false
  }
}

async function doApprove(a, result) {
  if (result === 2 && !(rejectReasons[a.id] || '').trim()) {
    toast('请先填写驳回原因', 'error')
    return
  }
  const ok = await confirm({
    title: result === 1 ? '通过申请' : '驳回申请',
    content: `${a.employeeName || '该同学'} · ${a.planName || '奖学金申请'}`,
    confirmText: result === 1 ? '通过' : '驳回',
    tone: result === 1 ? 'primary' : 'danger'
  })
  if (!ok) return
  await scholarshipApi.approve(a.id, result, result === 2 ? rejectReasons[a.id] : '')
  toast(result === 1 ? '已通过' : '已驳回')
  await loadAll()
}

async function doCancel(a) {
  const ok = await confirm({
    title: '撤回申请',
    content: '撤回后可以重新提交，确定撤回吗？',
    confirmText: '撤回',
    tone: 'danger'
  })
  if (!ok) return
  await scholarshipApi.cancel(a.id)
  toast('已撤回')
  await loadAll()
}

loadAll()
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center gap-3">
      <SegmentedTabs v-model="tab" :options="tabs" />
    </div>
    <p class="rounded-xl bg-slate-50 px-3 py-2 text-[11px] leading-relaxed text-slate-500">
      计分规则：GPA×6 + 红榜数×2 − 黑榜数×3（以计划配置为准）。公示期结束后排名与名额由学院审定。
    </p>

    <DataState :status="status" :error-text="error" :rows="4" @retry="loadAll">
      <!-- 可申请 -->
      <template v-if="tab === 'plans'">
        <p v-if="!plans.length" class="card py-10 text-center text-sm text-slate-400">
          当前没有面向你的奖学金计划
        </p>
        <ul v-else class="grid gap-3 md:grid-cols-2">
          <li v-for="p in plans" :key="p.id" class="card-flat flex flex-col p-4">
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0">
                <p class="truncate font-medium text-slate-900">{{ p.name }}</p>
                <p class="mt-0.5 truncate text-xs text-slate-400">
                  {{ p.term }} · 名额 {{ p.quota }} 人 · {{ p.amount }} 元
                </p>
              </div>
              <span class="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-600">
                {{ p.statusText }}
              </span>
            </div>

            <p class="mt-2 text-[11px] text-slate-400">
              报名 {{ (p.applyStart || '').slice(0, 10) }} ~ {{ (p.applyEnd || '').slice(0, 10) }}
            </p>

            <div class="mt-auto flex justify-end pt-3">
              <span v-if="p.applied" class="text-xs text-emerald-600">已提交申请</span>
              <button v-else-if="p.canApply" type="button" class="btn-sm bg-brand-500 text-white hover:bg-brand-600" @click="openApply(p)">
                申请
              </button>
              <span v-else class="text-xs text-slate-400">不在报名时间</span>
            </div>
          </li>
        </ul>
      </template>

      <!-- 我的申请 -->
      <template v-else-if="tab === 'mine'">
        <p v-if="!mine.length" class="card py-10 text-center text-sm text-slate-400">还没有申请记录</p>
        <ul v-else class="grid gap-3 md:grid-cols-2">
          <li v-for="a in mine" :key="a.id" class="card-flat p-4">
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0">
                <p class="truncate font-medium text-slate-900">{{ a.planName }}</p>
                <p class="mt-0.5 text-xs text-slate-500">
                  GPA {{ a.gpa }} · 加分 {{ a.extraScore }} · 综合分
                  <b class="text-slate-800">{{ a.totalScore }}</b>
                  <span v-if="a.rankNo"> · 第 {{ a.rankNo }} 名</span>
                </p>
              </div>
              <span class="shrink-0 rounded px-1.5 py-0.5 text-[10px]" :class="applyStatusClass(a.status)">
                {{ a.statusText }}
              </span>
            </div>
            <div v-if="a.status === 10" class="mt-3 flex justify-end border-t border-slate-100 pt-2">
              <button type="button" class="btn-text hover:text-rose-500" @click="doCancel(a)">撤回申请</button>
            </div>
          </li>
        </ul>
      </template>

      <!-- 待审批 -->
      <template v-else-if="tab === 'approve'">
        <p v-if="!pending.length" class="card py-10 text-center text-sm text-slate-400">没有待审批的申请</p>
        <ul v-else class="space-y-3">
          <li v-for="a in pending" :key="a.id" class="card-flat p-4">
            <div class="flex flex-wrap items-center gap-2">
              <p class="font-medium text-slate-900">{{ a.employeeName }}</p>
              <span class="text-xs text-slate-400">{{ a.employeeNo }}</span>
              <span class="ml-auto rounded bg-amber-50 px-1.5 py-0.5 text-[10px] text-amber-700">
                第 {{ a.currentStep }} 级审批
              </span>
            </div>
            <p class="mt-1 text-xs text-slate-500">
              {{ a.planName }} · GPA {{ a.gpa }} · 综合分 <b class="text-slate-700">{{ a.totalScore }}</b>
            </p>
            <p v-if="a.statement" class="mt-1 line-clamp-2 text-xs text-slate-500">陈述：{{ a.statement }}</p>

            <div class="mt-3 flex flex-col gap-2 border-t border-slate-100 pt-3 sm:flex-row">
              <input
                v-model="rejectReasons[a.id]"
                type="text"
                class="field !h-9 flex-1 text-[13px]"
                placeholder="驳回原因（驳回时必填）"
              />
              <div class="flex gap-2">
                <button type="button" class="btn-sm border border-rose-200 text-rose-600 hover:bg-rose-50" @click="doApprove(a, 2)">
                  驳回
                </button>
                <button type="button" class="btn-sm bg-brand-500 text-white hover:bg-brand-600" @click="doApprove(a, 1)">
                  通过
                </button>
              </div>
            </div>
          </li>
        </ul>
      </template>

      <!-- 公示 -->
      <template v-else>
        <p v-if="!publicity || !publicity.winners?.length" class="card py-10 text-center text-sm text-slate-400">
          暂无公示名单
        </p>
        <section v-else class="card">
          <header class="mb-1 flex items-center justify-between">
            <h2 class="section-title">公示名单</h2>
            <span class="text-[11px] text-slate-400">
              {{ (publicity.startTime || '').slice(0, 16) }} ~ {{ (publicity.endTime || '').slice(0, 16) }}
            </span>
          </header>
          <ul class="mt-2 divide-y divide-slate-100">
            <li
              v-for="w in publicity.winners"
              :key="w.employeeNo"
              class="flex items-center justify-between py-2.5 text-sm"
            >
              <span class="min-w-0 truncate">
                <span class="mr-2 text-[11px] text-slate-400">第 {{ w.rankNo }} 名</span>{{ w.employeeName }}
              </span>
              <span class="shrink-0 font-medium text-slate-600">{{ w.totalScore }}</span>
            </li>
          </ul>
        </section>
      </template>
    </DataState>

    <!-- 申请弹窗 -->
    <Teleport to="body">
      <div
        v-if="activePlan"
        class="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/40 p-4 backdrop-blur-sm sm:items-center"
        @click.self="activePlan = null"
      >
        <div class="w-full max-w-md animate-pop-in rounded-2xl bg-white p-5 shadow-pop">
          <h3 class="text-[15px] font-semibold text-slate-900">申请：{{ activePlan.name }}</h3>
          <p class="mt-1 text-[11px] text-slate-400">
            GPA×6 + 红榜数×2 − 黑榜数×3，提交后可在"我的申请"查看综合分与排名
          </p>

          <div class="mt-4 space-y-3">
            <label class="block">
              <span class="field-label">GPA（0~5）</span>
              <input v-model="form.gpa" type="number" step="0.1" min="0" max="5" class="field" />
            </label>
            <label class="block">
              <span class="field-label">个人陈述</span>
              <textarea
                v-model="form.statement"
                rows="3"
                class="field !h-auto py-2"
                placeholder="简要说明学习与获奖情况"
              />
            </label>
          </div>

          <div class="mt-5 flex gap-2.5">
            <button type="button" class="btn-ghost flex-1" @click="activePlan = null">取消</button>
            <button type="button" class="btn-solid flex-1" :disabled="submitting" @click="submitApply">
              {{ submitting ? '提交中…' : '提交申请' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
