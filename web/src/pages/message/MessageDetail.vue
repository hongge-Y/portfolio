<script setup>

import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { messageApi, levelClass, levelText } from "@/api/message";
const route = useRoute();
const router = useRouter();
const item = ref(null);
const loading = ref(true);
const errorText = ref("");
onMounted(async () => {
  try {
    const id = Number(route.params.id);
    item.value = route.query.box === "sent" ? await messageApi.sentDetail(id) : await messageApi.inboxDetail(id);
  } catch (e) {
    errorText.value = e?.message || "消息不存在或无权查看";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div>

    <div>
      <p v-if="loading" class="card text-center text-sm text-slate-400 py-8">加载中…</p>
      <div v-else-if="errorText" class="card text-center py-10">
        <div class="text-3xl">📭</div>
        <p class="mt-2 text-sm text-slate-500">{{ errorText }}</p>
        <button class="mt-2 text-xs text-brand-600 underline" @click="router.back()">返回列表</button>
      </div>
      <article v-else-if="item" class="card">
        <div class="flex items-center gap-2">
          <span class="px-1.5 py-0.5 rounded text-[10px]" :class="levelClass(item.level)">{{ levelText(item.level) }}</span>
          <span class="text-xs text-slate-400">
            {{ item.fromName || '系统' }} · {{ (item.createTime || item.sendTime || '').slice(0, 16) }}
          </span>
        </div>
        <h1 class="text-lg font-semibold mt-2">{{ item.title }}</h1>
        <div class="prose prose-sm max-w-none mt-4 text-slate-700 leading-relaxed" v-html="item.content" />
      </article>
    </div>
  </div>
</template>
