<script setup>
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { contentApi } from "@/api/content";
const router = useRouter();
const list = ref([]);
const loading = ref(true);
const errorText = ref("");
async function load() {
  loading.value = true;
  errorText.value = "";
  try {
    const res = await contentApi.matchPortal(1, 20);
    list.value = res.list;
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
      <p v-else-if="!list.length" class="card text-center text-sm text-slate-400 py-10">暂无可见赛事</p>
      <article
        v-for="m in list"
        :key="m.id"
        class="card cursor-pointer hover:shadow-md transition"
        @click="router.push(`/match/${m.id}`)"
      >
        <div class="flex items-start justify-between gap-2">
          <div>
            <p class="font-medium">{{ m.title }}</p>
            <p class="text-xs text-slate-400 mt-0.5">{{ m.subTitle || '—' }}</p>
            <p class="text-[11px] text-slate-300 mt-1">
              报名 {{ (m.startRegisterTime || '').slice(0, 10) }} ~ {{ (m.endRegisterTime || '').slice(0, 10) }}
            </p>
          </div>
          <span
            class="shrink-0 text-[10px] px-1.5 py-0.5 rounded"
            :class="m.canRegister ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'"
          >{{ m.canRegister ? '可报名' : '不可报名' }}</span>
        </div>
      </article>
    </div>
  </div>
</template>
