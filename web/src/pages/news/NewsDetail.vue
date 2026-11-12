<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { contentApi } from "@/api/content";
import { aiApi } from "@/api/ai";
import { humanMessage } from "@/utils/crypto";
import { toast } from "@/utils/toast";
const route = useRoute();
const router = useRouter();
const item = ref(null);
const loading = ref(true);
const liking = ref(false);
const aiLoading = ref(false);
const aiSummary = ref("");
const aiCost = ref(0);
async function load() {
  loading.value = true;
  try {
    item.value = await contentApi.newsDetail(Number(route.params.id));
  } finally {
    loading.value = false;
  }
}
async function summarize() {
  if (!item.value || aiLoading.value) return;
  aiLoading.value = true;
  try {
    const res = await aiApi.newsSummary(item.value.id);
    aiSummary.value = res.text;
    aiCost.value = res.costMs;
  } catch (e) {
    toast(humanMessage(e?.message, "AI 摘要失败"), "error");
  } finally {
    aiLoading.value = false;
  }
}
async function toggleLike() {
  if (liking.value) return;
  liking.value = true;
  try {
    const res = await contentApi.like("NEWS", item.value.id, !item.value.liked);
    item.value.liked = res.liked;
    item.value.likeCount = res.likeCount;
    toast(res.liked ? "已点赞" : "已取消");
  } finally {
    liking.value = false;
  }
}
onMounted(load);
</script>

<template>
  <div>

    <div>
      <p v-if="loading" class="card text-center text-sm text-slate-400 py-8">加载中…</p>
      <template v-else-if="item">
        <article class="card">
          <h1 class="text-xl font-semibold leading-snug">{{ item.title }}</h1>
          <p v-if="item.subTitle" class="text-sm text-slate-500 mt-1">{{ item.subTitle }}</p>
          <p class="text-xs text-slate-400 mt-2">
            {{ (item.startTime || '').slice(0, 16) }} · 浏览 {{ item.viewCount }} · 赞 {{ item.likeCount }}
          </p>

          <!-- AI 摘要：长通知先看这三句再决定要不要逐字读 -->
          <div class="mt-3">
            <button
              v-if="!aiSummary"
              class="flex items-center gap-1 rounded-lg bg-brand-50 px-3 py-1.5 text-xs font-medium text-brand-700 transition hover:bg-brand-100 disabled:opacity-50"
              :disabled="aiLoading"
              @click="summarize"
            >
              <span v-if="aiLoading" class="inline-block h-3 w-3 animate-spin rounded-full border-2 border-brand-500 border-t-transparent" />
              {{ aiLoading ? 'AI 正在读…' : '✨ AI 三句话读完' }}
            </button>
            <div v-else class="rounded-xl bg-brand-50/60 p-3">
              <div class="flex items-center justify-between">
                <span class="text-xs font-medium text-brand-700">AI 摘要（{{ aiCost }}ms）</span>
                <button class="text-[11px] text-slate-400 hover:text-rose-500" @click="aiSummary = ''">收起</button>
              </div>
              <p class="mt-1 whitespace-pre-wrap text-[13px] leading-relaxed text-slate-700">{{ aiSummary }}</p>
              <p class="mt-1 text-[10px] text-slate-400">摘要由模型生成，关键时间与材料请以原文为准</p>
            </div>
          </div>

          <div class="prose prose-sm max-w-none mt-4 text-slate-700 leading-relaxed" v-html="item.content || '<p>暂无正文</p>'" />
        </article>

        <div class="mt-3 flex justify-center">
          <button
            class="px-5 h-10 rounded-full border"
            :class="item.liked ? 'border-rose-200 bg-rose-50 text-rose-600' : 'border-slate-200 text-slate-600'"
            @click="toggleLike"
          >
            {{ item.liked ? '♥ 已赞' : '♡ 点赞' }} {{ item.likeCount }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>
