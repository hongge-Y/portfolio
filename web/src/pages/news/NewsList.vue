<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { contentApi } from "@/api/content";
const router = useRouter();
const list = ref([]);
const total = ref(0);
const pageNum = ref(1);
const loading = ref(true);
const errorText = ref("");
async function load() {
  loading.value = true;
  errorText.value = "";
  try {
    const res = await contentApi.newsPortal(pageNum.value, 10);
    list.value = res.list;
    total.value = res.total;
  } catch (e) {
    errorText.value = e?.message || "加载失败";
    list.value = [];
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>

<template>
  <div>

    <div class="space-y-3">
      <p v-if="loading" class="card text-center text-sm text-slate-400 py-8">加载中…</p>
      <div v-else-if="errorText" class="card text-center py-8">
        <p class="text-sm text-rose-500">{{ errorText }}</p>
        <button class="mt-2 text-xs text-brand-600 underline" @click="load()">重试</button>
      </div>
      <p v-else-if="!list.length" class="card text-center text-sm text-slate-400 py-10">
        暂无可见新闻（内容按你所属的组织过滤）
      </p>
      <article
        v-for="n in list"
        :key="n.id"
        class="card cursor-pointer hover:shadow-md transition"
        @click="router.push(`/news/${n.id}`)"
      >
        <div class="flex items-start gap-3">
          <div class="w-10 h-10 rounded-xl bg-brand-50 text-brand-600 grid place-items-center shrink-0">📰</div>
          <div class="min-w-0">
            <p class="font-medium truncate">{{ n.title }}</p>
            <p class="text-xs text-slate-400 mt-0.5">{{ n.subTitle || '—' }}</p>
            <p class="text-[11px] text-slate-300 mt-1">
              {{ (n.startTime || '').slice(0, 16) }} · 浏览 {{ n.viewCount ?? 0 }} · 赞 {{ n.likeCount ?? 0 }}
            </p>
          </div>
        </div>
      </article>

      <div v-if="total > 10" class="flex justify-center gap-3 pt-2">
        <button class="btn-primary !w-24 !h-9 text-sm" :disabled="pageNum <= 1" @click="pageNum--; load()">上一页</button>
        <button class="btn-primary !w-24 !h-9 text-sm" :disabled="pageNum * 10 >= total" @click="pageNum++; load()">下一页</button>
      </div>
    </div>
  </div>
</template>
