<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { adminApi } from "@/api/admin";
const router = useRouter();
const stats = ref({});
const loading = ref(true);
const cards = [
  { key: "users", label: "用户", to: "" },
  { key: "employees", label: "员工档案", to: "" },
  { key: "classes", label: "班级", to: "" },
  { key: "leaves", label: "请假单", to: "" },
  { key: "pendingLeaves", label: "待审批请假", to: "/leave/approve" },
  { key: "news", label: "新闻", to: "/admin/news" },
  { key: "ads", label: "广告", to: "/admin/ad" },
  { key: "matches", label: "赛事", to: "/admin/match" },
  { key: "ranks", label: "红黑榜", to: "/admin/rank" },
  { key: "plans", label: "奖学金计划", to: "/admin/scholarship" },
  { key: "schApplies", label: "奖学金申请", to: "/admin/scholarship" },
  { key: "pendingSchApplies", label: "待审奖学金", to: "/scholarship" }
];
onMounted(async () => {
  stats.value = await adminApi.stats();
  loading.value = false;
});
</script>

<template>
  <div class="space-y-4">
    <p class="text-xs text-slate-400">全站数据概览，数字来自 /v1/admin/stats；点卡片可跳到对应模块</p>
    <p v-if="loading" class="card text-sm text-slate-400 text-center py-8">加载中…</p>
    <div v-else class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <button
        v-for="c in cards"
        :key="c.key"
        class="card text-left hover:shadow-md transition"
        @click="c.to && router.push(c.to)"
      >
        <p class="text-2xl font-semibold">{{ stats[c.key] ?? 0 }}</p>
        <p class="text-xs text-slate-400 mt-1">{{ c.label }}</p>
      </button>
    </div>
    <p class="text-xs text-slate-400">
      后台只做内容与评审管理；请假审批在「待审批」页（前台同一套接口）。
    </p>
  </div>
</template>
