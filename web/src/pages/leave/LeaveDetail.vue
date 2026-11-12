<script setup>
import { useConfirm } from '@/composables/useConfirm'
import { statusClass } from '@/utils/format';

import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { leaveApi } from "@/api/leave";
import { aiApi } from "@/api/ai";
import { humanMessage } from "@/utils/crypto";
import { useUserStore } from "@/stores/user";
import { toast } from "@/utils/toast";

const { confirm } = useConfirm()
const route = useRoute();
const router = useRouter();
const store = useUserStore();
const item = ref(null);
const loading = ref(true);
const rejectReason = ref("");
const showReject = ref(false);
const aiLoading = ref(false);
const aiSuggest = ref("");
const aiCost = ref(0);
const aiError = ref("");
const canApprove = computed(
  () => store.hasPerm("leave:approve") && !!item.value && [10, 11].includes(item.value.status)
);
async function load() {
  loading.value = true;
  try {
    item.value = await leaveApi.detail(Number(route.params.id));
  } finally {
    loading.value = false;
  }
}
async function askAiSuggest() {
  if (!item.value || aiLoading.value) return;
  aiLoading.value = true;
  aiError.value = "";
  try {
    const res = await aiApi.approveSuggest(item.value.id);
    aiSuggest.value = res.text;
    aiCost.value = res.costMs;
  } catch (e) {
    // 兜底：超时/格式跑偏/网络失败都不阻塞审批，退回人工填写
    aiSuggest.value = "";
    aiError.value = "AI 生成失败，请手动填写审批意见";
    toast(humanMessage(e?.message, "AI 建议获取失败，请手动填写"), "error");
  } finally {
    aiLoading.value = false;
  }
}
function useAiAsReject() {
  const m = aiSuggest.value.match(/【意见草稿】\s*([\s\S]+)/);
  rejectReason.value = (m ? m[1] : aiSuggest.value).trim();
  showReject.value = true;
}
async function approve(result, reason) {
  if (result === 2 && !reason?.trim()) return toast("驳回必须填写原因", "error");
  await leaveApi.approve({ leaveId: item.value.id, result, reason });
  toast(result === 1 ? "已批准" : "已驳回");
  showReject.value = false;
  rejectReason.value = "";
  load();
}
async function doCancel() {
  const ok = await confirm({ content: "确认取消？" })
  if (!ok) return
  await leaveApi.cancel(item.value.id);
  toast("已取消");
  load();
}
async function doReturn() {
  const ok = await confirm({ content: "确认销假？" })
  if (!ok) return
  await leaveApi.returnLeave(item.value.id);
  toast("销假成功");
  load();
}
onMounted(load);
</script>

<template>
  <div>

    <div class="space-y-3">
      <div v-if="loading" class="card text-center text-sm text-slate-400 py-8">加载中…</div>

      <template v-else-if="item">
        <section class="card">
          <div class="flex items-start justify-between">
            <div>
              <h2 class="text-lg font-semibold">{{ item.employeeName || store.me?.name }}</h2>
              <p class="text-xs text-slate-400 mt-0.5">{{ item.employeeNo }} · {{ item.className }}</p>
            </div>
            <span class="px-2 py-1 rounded-md text-xs font-medium" :class="statusClass(item.status)">
              {{ item.statusText }}
            </span>
          </div>

          <dl class="mt-4 space-y-2 text-sm">
            <div class="flex justify-between"><dt class="text-slate-500">类型</dt><dd>{{ item.typeText }}</dd></div>
            <div class="flex justify-between"><dt class="text-slate-500">开始</dt><dd>{{ item.startTime }}</dd></div>
            <div class="flex justify-between"><dt class="text-slate-500">结束</dt><dd>{{ item.endTime }}</dd></div>
            <div class="flex justify-between"><dt class="text-slate-500">天数</dt><dd>{{ item.days }} 天</dd></div>
            <div class="flex justify-between" v-if="item.returnTime">
              <dt class="text-slate-500">销假时间</dt><dd>{{ item.returnTime }}</dd>
            </div>
            <div class="pt-2 border-t border-slate-100">
              <dt class="text-slate-500 mb-1">请假理由</dt>
              <dd class="text-slate-700 whitespace-pre-wrap">
                {{ item.reason || '（无权限查看 / 未填写）' }}
              </dd>
            </div>
          </dl>
        </section>

        <section class="card">
          <h3 class="font-medium text-sm mb-3">审批流程</h3>
          <ol class="relative border-l border-slate-200 ml-2 space-y-4">
            <li v-for="n in item.approvals || []" :key="n.step" class="pl-4">
              <span
                class="absolute -left-[5px] mt-1.5 w-2.5 h-2.5 rounded-full"
                :class="n.result === 1 ? 'bg-emerald-500' : n.result === 2 ? 'bg-rose-500' : 'bg-slate-300'"
              />
              <p class="text-sm">
                第 {{ n.step }} 级 · {{ n.approverName }}
                <span class="text-xs text-slate-400">（{{ n.approverRole === 'COUNSELOR' ? '辅导员' : '书记' }}）</span>
              </p>
              <p class="text-xs mt-0.5" :class="n.result === 1 ? 'text-emerald-600' : n.result === 2 ? 'text-rose-600' : 'text-slate-400'">
                {{ n.resultText }}
                <span v-if="n.reason">· {{ n.reason }}</span>
                <span v-if="n.handleTime"> · {{ n.handleTime }}</span>
              </p>
            </li>
          </ol>
        </section>

        <div v-if="canApprove" class="card space-y-2">
          <p class="text-sm text-slate-500">当前待你审批（第 {{ item.currentStep }} 级）</p>
          <div class="flex gap-2">
            <button class="btn-primary !w-auto px-4" @click="approve(1)">批准</button>
            <button class="h-11 px-4 rounded-xl border border-rose-200 text-rose-600" @click="showReject = true">驳回</button>
            <button
              class="h-11 px-4 rounded-xl border border-brand-200 bg-brand-50 text-sm font-medium text-brand-700 disabled:opacity-50"
              :disabled="aiLoading"
              @click="askAiSuggest"
            >
              {{ aiLoading ? 'AI 分析中…' : '✨ AI 建议' }}
            </button>
          </div>

          <!-- AI 只是辅助：明确标注仅供参考，决策仍由审批人做 -->
          <p v-if="aiError" class="mt-2 text-[12px] text-rose-500">{{ aiError }}</p>
          <div v-if="aiSuggest" class="rounded-xl bg-brand-50/60 p-3 text-[13px] leading-relaxed text-slate-700">
            <div class="mb-1 flex items-center justify-between">
              <span class="text-xs font-medium text-brand-700">AI 审批参考（耗时 {{ aiCost }}ms）</span>
              <button class="text-[11px] text-slate-400 hover:text-rose-500" @click="aiSuggest = ''">收起</button>
            </div>
            <p class="whitespace-pre-wrap">{{ aiSuggest }}</p>
            <button class="mt-2 text-[11px] text-brand-600 underline" @click="useAiAsReject">
              把「意见草稿」填进驳回框
            </button>
            <p class="mt-1 text-[10px] text-slate-400">AI 依据的是本单已有信息（材料、时长、层级），最终结论请自行判断</p>
          </div>

          <div v-if="showReject" class="pt-2 space-y-2">
            <textarea v-model="rejectReason" rows="2" class="field !h-auto py-2" placeholder="驳回原因（必填）" />
            <button class="btn-primary" @click="approve(2, rejectReason)">确认驳回</button>
          </div>
        </div>

        <div class="flex gap-2" v-if="!canApprove">
          <button
            v-if="[10, 11, 30].includes(item.status)"
            class="flex-1 h-11 rounded-xl border border-slate-200 text-slate-600"
            @click="doCancel"
          >取消请假</button>
          <button v-if="item.status === 20" class="flex-1 btn-primary" @click="doReturn">销假</button>
        </div>
      </template>
    </div>
  </div>
</template>
