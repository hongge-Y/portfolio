<script setup>
import { useConfirm } from '@/composables/useConfirm'
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { contentApi } from "@/api/content";
import { toast } from "@/utils/toast";

const { confirm } = useConfirm()
const route = useRoute();
const router = useRouter();
const item = ref(null);
const loading = ref(true);
const teamName = ref("");
const contact = ref("");
const submitting = ref(false);
async function load() {
  loading.value = true;
  try {
    item.value = await contentApi.matchDetail(Number(route.params.id));
  } finally {
    loading.value = false;
  }
}
async function register() {
  const ok = await confirm({ content: "确认报名该赛事？" })
  if (!ok) return
  submitting.value = true;
  try {
    await contentApi.registerMatch(item.value.id, {
      teamName: teamName.value || null,
      contact: contact.value || null
    });
    toast("报名成功，等待审核");
    load();
  } finally {
    submitting.value = false;
  }
}
onMounted(load);
</script>

<template>
  <div>

    <div class="space-y-3">
      <p v-if="loading" class="card text-center text-sm text-slate-400 py-8">加载中…</p>
      <template v-else-if="item">
        <article class="card">
          <h1 class="text-xl font-semibold">{{ item.title }}</h1>
          <p v-if="item.subTitle" class="text-sm text-slate-500 mt-1">{{ item.subTitle }}</p>
          <dl class="mt-3 grid grid-cols-2 gap-y-1.5 text-sm">
            <dt class="text-slate-400">报名时间</dt>
            <dd class="text-right">{{ (item.startRegisterTime || '').slice(0, 10) }} ~ {{ (item.endRegisterTime || '').slice(0, 10) }}</dd>
            <dt class="text-slate-400">比赛时间</dt>
            <dd class="text-right">{{ (item.matchTime || '').slice(0, 16) }}</dd>
            <dt class="text-slate-400">组队人数</dt>
            <dd class="text-right">{{ item.groupCount }} 人</dd>
          </dl>
          <div class="prose prose-sm max-w-none mt-4 text-slate-700" v-html="item.content || '<p>暂无详情</p>'" />
        </article>

        <div v-if="item.registered" class="card text-center text-sm text-emerald-600">你已报名，等待审核</div>
        <div v-else-if="item.canRegister" class="card space-y-2">
          <p class="text-sm text-slate-500">报名信息</p>
          <input v-model="teamName" class="field" placeholder="队伍名称（个人赛可留空）" />
          <input v-model="contact" class="field" placeholder="联系方式" />
          <button class="btn-primary" :disabled="submitting" @click="register">
            {{ submitting ? '提交中…' : '立即报名' }}
          </button>
        </div>
        <div v-else class="card text-center text-sm text-slate-400">当前不在报名时间内</div>
      </template>
    </div>
  </div>
</template>
