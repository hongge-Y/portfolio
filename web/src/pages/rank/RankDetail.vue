<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { contentApi } from "@/api/content";
import { toast } from "@/utils/toast";
const route = useRoute();
const router = useRouter();
const item = ref(null);
const loading = ref(true);
async function load() {
  loading.value = true;
  try {
    item.value = await contentApi.rankDetail(Number(route.params.id));
  } finally {
    loading.value = false;
  }
}
async function toggleLike() {
  const res = await contentApi.like("RANK", item.value.id, !item.value.liked);
  item.value.liked = res.liked;
  item.value.likeCount = res.likeCount;
  toast(res.liked ? "已点赞" : "已取消");
}
onMounted(load);
</script>

<template>
  <div>

    <div class="space-y-3">
      <p v-if="loading" class="card text-center text-sm text-slate-400 py-8">加载中…</p>
      <template v-else-if="item">
        <article class="card">
          <div class="flex items-center gap-2">
            <span
              class="px-1.5 py-0.5 rounded text-[10px] font-medium"
              :class="item.red ? 'bg-rose-50 text-rose-600' : 'bg-slate-800 text-white'"
            >{{ item.red ? '红榜' : '黑榜' }}</span>
            <span v-if="item.labelName" class="text-xs text-slate-500">标签：{{ item.labelName }}</span>
          </div>
          <h1 class="text-xl font-semibold mt-2">{{ item.title }}</h1>
          <p v-if="item.subTitle" class="text-sm text-slate-500 mt-1">{{ item.subTitle }}</p>
          <p class="text-xs text-slate-400 mt-2">
            {{ item.employeeName }} · {{ (item.updateTime || '').slice(0, 10) }}
          </p>
          <p v-if="item.achievement" class="mt-3 text-sm text-slate-700 bg-slate-50 rounded-xl p-3">
            {{ item.achievement }}
          </p>
          <div class="prose prose-sm max-w-none mt-4 text-slate-700" v-html="item.content || ''" />
        </article>

        <div class="flex justify-center">
          <button
            class="px-5 h-10 rounded-full border"
            :class="item.liked ? 'border-rose-200 bg-rose-50 text-rose-600' : 'border-slate-200 text-slate-600'"
            @click="toggleLike"
          >{{ item.liked ? '♥ 已赞' : '♡ 点赞' }} {{ item.likeCount }}</button>
        </div>
      </template>
    </div>
  </div>
</template>
