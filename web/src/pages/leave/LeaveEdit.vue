<script setup>
import { useConfirm } from '@/composables/useConfirm'
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { leaveApi } from "@/api/leave";
import { aiApi } from "@/api/ai";
import { humanMessage } from "@/utils/crypto";
import { calcDays, toApiDateTime } from "@/utils/format";
import { toast } from "@/utils/toast";

const { confirm } = useConfirm()
const router = useRouter();
const rule = ref(null);
const submitting = ref(false);
const aiLoading = ref(false);
const form = reactive({
  type: 1,
  startTime: "",
  endTime: "",
  reason: ""
});
const days = computed(() => calcDays(form.startTime, form.endTime));
const needSecretary = computed(() => !!rule.value && days.value > rule.value.secretaryThresholdDays);
const tooLong = computed(() => !!rule.value && days.value > rule.value.maxDays);
onMounted(async () => {
  rule.value = await leaveApi.rule();
});
async function aiWriteReason() {
  if (aiLoading.value) return;
  aiLoading.value = true;
  try {
    const res = await aiApi.leaveDraft({
      type: form.type,
      startTime: form.startTime ? toApiDateTime(form.startTime) : void 0,
      endTime: form.endTime ? toApiDateTime(form.endTime) : void 0,
      days: days.value || void 0,
      remark: form.reason.trim() || void 0
    });
    form.reason = res.text;
    toast(`AI 已生成（${res.costMs}ms）`);
  } catch (e) {
    toast(humanMessage(e?.message, "AI 生成失败，请稍后重试"), "error");
  } finally {
    aiLoading.value = false;
  }
}
async function submit() {
  if (!form.startTime || !form.endTime) return toast("请选择请假时间", "error");
  if (!form.reason.trim()) return toast("请填写请假原因", "error");
  if (tooLong.value) return toast(`超过 ${rule.value?.maxDays} 天，请前往一站式服务中心`, "error");
  const flow = needSecretary.value ? "辅导员 → 书记" : "辅导员";
  const ok = await confirm({ content: `共 ${days.value} 天，审批流程：${flow}
确认提交？` })
  if (!ok) return
  submitting.value = true;
  try {
    const res = await leaveApi.apply({
      type: form.type,
      startTime: toApiDateTime(form.startTime),
      endTime: toApiDateTime(form.endTime),
      reason: form.reason
    });
    toast(`提交成功，${res.approveFlow?.length || 1} 个审批节点`);
    router.replace("/leave");
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div>

    <div class="space-y-3">
      <div class="card space-y-3">
        <div>
          <label class="text-sm text-slate-600">请假类型</label>
          <div class="mt-2 flex gap-2">
            <button
              v-for="t in rule?.types || []"
              :key="t.value"
              class="flex-1 h-10 rounded-xl border text-sm"
              :class="form.type === t.value ? 'border-brand-500 bg-brand-50 text-brand-700 font-medium' : 'border-slate-200'"
              @click="form.type = t.value"
            >{{ t.label }}</button>
          </div>
        </div>

        <div>
          <label class="text-sm text-slate-600">开始时间</label>
          <input v-model="form.startTime" type="datetime-local" class="field mt-2" />
        </div>
        <div>
          <label class="text-sm text-slate-600">结束时间</label>
          <input v-model="form.endTime" type="datetime-local" class="field mt-2" />
        </div>

        <div
          v-if="days > 0"
          class="rounded-xl px-3 py-2 text-sm"
          :class="tooLong ? 'bg-rose-50 text-rose-700' : 'bg-slate-50 text-slate-600'"
        >
          共 <b>{{ days }}</b> 天
          <template v-if="tooLong"> · 超过 {{ rule?.maxDays }} 天上限，请前往一站式服务中心</template>
          <template v-else> · 审批流程：{{ needSecretary ? '辅导员 → 书记' : '辅导员' }}</template>
        </div>

        <div>
          <div class="flex items-center justify-between">
            <label class="text-sm text-slate-600">请假原因</label>
            <button
              class="flex items-center gap-1 rounded-lg bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-700 transition hover:bg-brand-100 disabled:opacity-50"
              :disabled="aiLoading"
              @click="aiWriteReason"
            >
              <span v-if="aiLoading" class="inline-block h-3 w-3 animate-spin rounded-full border-2 border-brand-500 border-t-transparent" />
              {{ aiLoading ? 'AI 正在写…' : '✨ AI 帮写' }}
            </button>
          </div>
          <textarea v-model="form.reason" rows="4" class="field mt-2 !h-auto py-2" placeholder="请说明事由，病假建议附诊断证明；也可以只写几个关键词，让 AI 帮你润色" />
          <p class="mt-1 text-[11px] text-slate-400">AI 生成的内容请自己核对一遍再提交，审批责任仍在申请人</p>
        </div>
      </div>

      <p class="text-xs text-slate-400 leading-relaxed">
        规则：流程中的假条 ≤ {{ rule?.maxInFlight }} 条；同一时间段不可重复请假；单次 ≤ {{ rule?.maxDays }} 天。
      </p>

      <button class="btn-primary" :disabled="submitting || tooLong" @click="submit">
        {{ submitting ? '提交中…' : '提交申请' }}
      </button>
    </div>
  </div>
</template>
