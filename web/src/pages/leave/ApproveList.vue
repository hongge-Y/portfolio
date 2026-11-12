<script setup>
import { statusClass } from '@/utils/format';

import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { leaveApi } from "@/api/leave";
import { toast } from "@/utils/toast";
const router = useRouter();
const list = ref([]);
const total = ref(0);
const loading = ref(true);
const errorText = ref("");
async function load() {
  loading.value = true;
  errorText.value = "";
  try {
    const res = await leaveApi.approvePage({ pageNum: 1, pageSize: 50, searchModel: {} });
    list.value = res.list;
    total.value = res.total;
  } catch (e) {
    errorText.value = e?.message || "加载失败";
    list.value = [];
  } finally {
    loading.value = false;
  }
}
async function quickApprove(item, result) {
  let reason = "";
  if (result === 2) {
    reason = prompt("请输入驳回原因（必填）") || "";
    if (!reason.trim()) return;
  }
  await leaveApi.approve({ leaveId: item.id, result, reason });
  toast(result === 1 ? "已批准" : "已驳回");
  load();
}
onMounted(load);
</script>

<template>
  <div>

    <div class="space-y-3">
      <div v-if="loading" class="card text-center text-sm text-slate-400 py-8">加载中…</div>
      <div v-else-if="errorText" class="card text-center py-8">
        <p class="text-sm text-rose-500">{{ errorText }}</p>
        <button class="mt-2 text-xs text-brand-600 underline" @click="load()">重试</button>
      </div>
      <div v-else-if="list.length === 0" class="card text-center py-10">
        <div class="text-3xl">✅</div>
        <p class="mt-2 text-sm text-slate-400">没有待审批的请假</p>
      </div>

      <article v-for="item in list" :key="item.id" class="card">
        <div class="flex items-start justify-between">
          <div>
            <p class="font-medium">{{ item.employeeName }} <span class="text-xs text-slate-400">{{ item.employeeNo }}</span></p>
            <p class="text-xs text-slate-500 mt-0.5">{{ item.className }}</p>
          </div>
          <span class="px-2 py-1 rounded-md text-xs" :class="statusClass(item.status)">{{ item.statusText }}</span>
        </div>
        <p class="mt-2 text-sm">{{ item.startTime.slice(0, 16) }} → {{ item.endTime.slice(0, 16) }} · {{ item.days }} 天 · {{ item.typeText }}</p>
        <p v-if="item.reason" class="mt-1 text-xs text-slate-500 line-clamp-2">理由：{{ item.reason }}</p>
        <div class="mt-3 flex gap-2 justify-end">
          <button class="px-3 h-8 rounded-lg text-xs border border-slate-200" @click="router.push(`/leave/${item.id}`)">详情</button>
          <button class="px-3 h-8 rounded-lg text-xs border border-rose-200 text-rose-600" @click="quickApprove(item, 2)">驳回</button>
          <button class="px-3 h-8 rounded-lg text-xs bg-brand-500 text-white" @click="quickApprove(item, 1)">批准</button>
        </div>
      </article>
    </div>
  </div>
</template>
